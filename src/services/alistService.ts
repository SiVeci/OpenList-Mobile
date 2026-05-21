import axios, { AxiosInstance } from 'axios';
import { AuthResponse, FsListResponse, FsGetResponse, ServerConfig } from '../types';

import ReactNativeBlobUtil from 'react-native-blob-util';

export class AListService {
  private baseUrl: string;
  private token: string;
  private client: AxiosInstance;

  constructor(config: ServerConfig) {
    this.baseUrl = config.url.endsWith('/') ? config.url.slice(0, -1) : config.url;
    this.token = config.token;
    this.client = axios.create({
      baseURL: this.baseUrl,
      timeout: 20000,
    });
  }

  private getHeaders() {
    return {
      'Content-Type': 'application/json',
      'Authorization': this.token,
      'AList-Token': this.token,
    };
  }

  static async login(url: string, username: string, password: string): Promise<string> {
    const cleanUrl = url.endsWith('/') ? url.slice(0, -1) : url;
    const response = await axios.post<AuthResponse>(`${cleanUrl}/api/auth/login`, {
      username,
      password,
    });
    const json = response.data;
    if (json.code !== 200) {
      throw new Error(json.message || 'Authentication failed');
    }
    return json.data.token;
  }

  async listFiles(path: string, page = 1, perPage = 100, refresh = false): Promise<FsListResponse> {
    const response = await this.client.post<FsListResponse>('/api/fs/list', {
      path,
      page,
      per_page: perPage,
      refresh,
    }, { headers: this.getHeaders() });
    const res = response.data;
    if (res.code !== 200) {
      throw new Error(res.message || 'AList API Error');
    }
    return res;
  }

  async getFileDetail(path: string): Promise<FsGetResponse> {
    const response = await this.client.post<FsGetResponse>('/api/fs/get', {
      path,
    }, { headers: this.getHeaders() });
    const res = response.data;
    if (res.code !== 200) {
      throw new Error(res.message || 'AList API Error');
    }
    return res;
  }

  async deleteFiles(dir: string, names: string[]): Promise<void> {
    const response = await this.client.post('/api/fs/remove', {
      dir,
      names,
    }, { headers: this.getHeaders() });
    const json = response.data;
    if (json.code !== 200) {
      throw new Error(json.message || 'Delete failed');
    }
  }

  async uploadFile(dirPath: string, fileUri: string, fileName: string, mimeType: string): Promise<void> {
    let cleanDir = dirPath.startsWith('/') ? dirPath : '/' + dirPath;
    if (cleanDir !== '/' && cleanDir.endsWith('/')) {
      cleanDir = cleanDir.slice(0, -1);
    }
    const fullPath = cleanDir === '/' ? `/${fileName}` : `${cleanDir}/${fileName}`;

    // For content:// URIs, react-native-blob-util can read them if prefixed properly or handled directly
    const realUri = fileUri.startsWith('content://') ? fileUri : fileUri.replace('file://', '');

    const response = await ReactNativeBlobUtil.fetch(
      'PUT',
      `${this.baseUrl}/api/fs/put`,
      {
        'Authorization': this.token,
        'AList-Token': this.token,
        'File-Path': encodeURIComponent(fullPath),
        'As-Task': 'false',
        'Content-Type': mimeType || 'application/octet-stream',
      },
      ReactNativeBlobUtil.wrap(realUri)
    );

    const json = JSON.parse(response.data);
    if (json.code !== 200) {
      throw new Error(json.message || 'Upload failed');
    }
  }
}
