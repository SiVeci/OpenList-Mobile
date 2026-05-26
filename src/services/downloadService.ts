import { AListService } from './alistService';
import { ServerConfig } from '../types';
import DownloadNative from '../native/NativeDownload';
import { DeviceEventEmitter, EmitterSubscription } from 'react-native';

export interface DownloadTask {
  id: string;
  fileName: string;
  fileSize: number;
  remotePath: string;
  localDirUri: string;
  progress: number;
  status: 'pending' | 'downloading' | 'complete' | 'error' | 'cancelled';
  error?: string;
}

class DownloadService {
  private subscriptions: EmitterSubscription[] = [];

  initListeners(
    onProgressCb?: (fileName: string, progress: number) => void,
    onCompleteCb?: (fileName: string, uri: string) => void,
    onErrorCb?: (fileName: string, error: string) => void,
    onCancelCb?: (fileName: string) => void,
  ) {
    this.removeAllListeners();
    if (onProgressCb) {
      this.subscriptions.push(DeviceEventEmitter.addListener('onDownloadProgress', data => onProgressCb(data.fileName, data.progress)));
    }
    if (onCompleteCb) {
      this.subscriptions.push(DeviceEventEmitter.addListener('onDownloadComplete', data => onCompleteCb(data.fileName, data.uri)));
    }
    if (onErrorCb) {
      this.subscriptions.push(DeviceEventEmitter.addListener('onDownloadError', data => onErrorCb(data.fileName, data.error)));
    }
    if (onCancelCb) {
      this.subscriptions.push(DeviceEventEmitter.addListener('onDownloadCancelled', data => onCancelCb(data.fileName)));
    }
  }

  removeAllListeners() {
    this.subscriptions.forEach(s => s.remove());
    this.subscriptions = [];
  }

  async startDownload(
    config: ServerConfig,
    remotePath: string,
    localDirUri: string,
    fileSize: number,
    mimeType: string,
  ): Promise<boolean> {
    const service = new AListService(config);
    const detail = await service.getFileDetail(remotePath);
    
    const rawUrl = detail.data.raw_url;
    const fileName = remotePath.split('/').pop() || 'download';
    const authHeader = config.token;

    try {
      await DownloadNative.startDownload(
        rawUrl,
        localDirUri,
        fileName,
        fileSize,
        mimeType || 'application/octet-stream',
        authHeader
      );
      return true;
    } catch (e: any) {
      DeviceEventEmitter.emit('onDownloadError', { fileName, error: e.message || 'Download failed' });
      return false;
    }
  }

  async cancelDownload(fileName: string): Promise<boolean> {
    try {
      await DownloadNative.cancelDownload(fileName);
      return true;
    } catch (e) {
      return false;
    }
  }
}

export const downloadService = new DownloadService();
