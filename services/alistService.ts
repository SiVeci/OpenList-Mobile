
import { AuthResponse, FsListResponse, FsGetResponse, ServerConfig } from '../types';

export class AListService {
  private baseUrl: string;
  private token: string;
  private isHttps: boolean;

  constructor(config: ServerConfig) {
    this.baseUrl = config.url.endsWith('/') ? config.url.slice(0, -1) : config.url;
    this.token = config.token;
    this.isHttps = this.baseUrl.startsWith('https://');
  }

  private fixProtocol(url: string | undefined): string {
    if (!url) return '';
    if (this.isHttps && url.startsWith('http://')) {
      return url.replace('http://', 'https://');
    }
    return url;
  }

  private async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const headers = {
      'Content-Type': 'application/json',
      'Authorization': this.token,
      'AList-Token': this.token, // Some AList setups specifically require this
      ...(options.headers || {}),
    };

    const response = await fetch(`${this.baseUrl}${path}`, {
      ...options,
      headers,
    });

    if (!response.ok) {
      if (response.status === 401) {
        throw new Error('Unauthorized: Token may be expired');
      }
      throw new Error(`Request failed with status ${response.status}`);
    }

    const json = await response.json();
    if (json.code !== 200) {
      throw new Error(json.message || 'API Error');
    }
    return json as T;
  }

  static async login(url: string, username: string, password: string): Promise<string> {
    const cleanUrl = url.endsWith('/') ? url.slice(0, -1) : url;
    const response = await fetch(`${cleanUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });

    const json: AuthResponse = await response.json();
    if (json.code !== 200) {
      throw new Error(json.message || 'Login failed');
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

  async deleteFile(path: string): Promise<void> {
    // AList V3 remove API requires dir (parent path) and names array
    const lastSlashIndex = path.lastIndexOf('/');
    
    // Handle root files vs subdirectory files
    let dir = '/';
    let name = path;

    if (lastSlashIndex !== -1) {
      dir = path.substring(0, lastSlashIndex) || '/';
      name = path.substring(lastSlashIndex + 1);
    }

    await this.request('/api/fs/remove', {
      method: 'POST',
      body: JSON.stringify({ 
        dir: dir,
        names: [name]
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
      throw new Error(`Upload HTTP error: ${response.status}`);
    }

    const json = await response.json();
    if (json.code !== 200) {
      throw new Error(json.message || 'Upload failed');
    }
  }
}
