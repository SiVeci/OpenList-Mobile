
import { AuthResponse, FsListResponse, FsGetResponse, ServerConfig } from '../types';

export class AListService {
  private baseUrl: string;
  private token: string;
  private protocol: string;

  constructor(config: ServerConfig) {
    this.baseUrl = config.url.endsWith('/') ? config.url.slice(0, -1) : config.url;
    this.token = config.token;
    // Extract protocol to manage networking strategy
    this.protocol = this.baseUrl.startsWith('https://') ? 'https://' : 'http://';
  }

  /**
   * Adjusts the protocol of incoming URLs (like thumb/raw_url) to match the server configuration
   * to avoid mixed content issues or to ensure encrypted traffic if requested.
   */
  private fixProtocol(url: string | undefined): string {
    if (!url) return '';
    
    // If we are using HTTPS base, we should try to promote http resources to https
    if (this.protocol === 'https://' && url.startsWith('http://')) {
      return url.replace('http://', 'https://');
    }
    
    // If the server explicitly requested HTTP, we stick to what the server returns
    // unless it's a relative path.
    if (url.startsWith('/')) {
      return `${this.baseUrl}${url}`;
    }
    
    return url;
  }

  private async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 20000); // Increased to 20s for mobile networks

    const headers = {
      'Content-Type': 'application/json',
      'Authorization': this.token,
      'AList-Token': this.token,
      ...(options.headers || {}),
    };

    try {
      const response = await fetch(`${this.baseUrl}${path}`, {
        ...options,
        headers,
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        if (response.status === 401) {
          throw new Error('Unauthorized: Session expired. Please log in again.');
        }
        throw new Error(`API Request Error: ${response.status}`);
      }

      const json = await response.json();
      if (json.code !== 200) {
        throw new Error(json.message || 'AList API Error');
      }
      return json as T;
    } catch (err: any) {
      if (err.name === 'AbortError') throw new Error('Request timed out. Server might be under heavy load.');
      throw err;
    }
  }

  /**
   * Static login method to verify credentials and obtain a token.
   * Now takes the synthesized URL from the 3-part input UI.
   */
  static async login(url: string, username: string, password: string): Promise<string> {
    const cleanUrl = url.endsWith('/') ? url.slice(0, -1) : url;
    
    const response = await fetch(`${cleanUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
      throw new Error(`Connection failed: ${response.status}`);
    }

    const json: AuthResponse = await response.json();
    if (json.code !== 200) {
      throw new Error(json.message || 'Authentication failed');
    }
    return json.data.token;
  }

  async listFiles(path: string, page = 1, perPage = 100, refresh = false): Promise<FsListResponse> {
    const res = await this.request<FsListResponse>('/api/fs/list', {
      method: 'POST',
      body: JSON.stringify({
        path,
        page,
        per_page: perPage,
        refresh: refresh
      }),
    });

    if (res.data && res.data.content) {
      res.data.content = res.data.content.map(file => ({
        ...file,
        thumb: this.fixProtocol(file.thumb)
      }));
    }
    return res;
  }

  async getFileDetail(path: string): Promise<FsGetResponse> {
    const res = await this.request<FsGetResponse>('/api/fs/get', {
      method: 'POST',
      body: JSON.stringify({ path }),
    });

    if (res.data) {
      res.data.raw_url = this.fixProtocol(res.data.raw_url);
      res.data.thumb = this.fixProtocol(res.data.thumb);
    }
    return res;
  }

  async deleteFiles(dir: string, names: string[]): Promise<void> {
    await this.request('/api/fs/remove', {
      method: 'POST',
      body: JSON.stringify({ 
        dir: dir,
        names: names
      }),
    });
  }

  async uploadFile(dirPath: string, file: File): Promise<void> {
    let cleanDir = dirPath.startsWith('/') ? dirPath : '/' + dirPath;
    if (cleanDir !== '/' && cleanDir.endsWith('/')) {
      cleanDir = cleanDir.slice(0, -1);
    }

    const fullPath = cleanDir === '/' ? `/${file.name}` : `${cleanDir}/${file.name}`;

    const headers = new Headers();
    headers.append('Authorization', this.token);
    headers.append('AList-Token', this.token);
    headers.append('File-Path', encodeURIComponent(fullPath));
    headers.append('As-Task', 'false');
    headers.append('Content-Type', file.type || 'application/octet-stream');

    const response = await fetch(`${this.baseUrl}/api/fs/put`, {
      method: 'PUT',
      headers,
      body: file,
    });

    if (!response.ok) {
      throw new Error(`Upload failed: Server returned ${response.status}`);
    }

    const json = await response.json();
    if (json.code !== 200) {
      throw new Error(json.message || 'Upload task failed');
    }
  }
}
