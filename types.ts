
export interface ServerConfig {
  url: string;
  username: string;
  token: string;
  serverName: string;
}

export interface AListFile {
  name: string;
  size: number;
  is_dir: boolean;
  modified: string;
  sign: string;
  thumb: string;
  type: number;
}

export interface FsListResponse {
  code: number;
  message: string;
  data: {
    content: AListFile[];
    total: number;
    readme: string;
    header: string;
  };
}

export interface FsGetResponse {
  code: number;
  message: string;
  data: AListFile & {
    raw_url: string;
    provider: string;
    related: any;
  };
}

export interface AuthResponse {
  code: number;
  message: string;
  data: {
    token: string;
  };
}
