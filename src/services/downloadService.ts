import { AListService } from './alistService';
import { ServerConfig } from '../types';
import ReactNativeBlobUtil from 'react-native-blob-util';
import { localFsService } from './localFsService';
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
  private activeTasks: Map<string, any> = new Map();

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

    // Use react-native-blob-util to download to cache dir
    const dirs = ReactNativeBlobUtil.fs.dirs;
    const cachePath = `${dirs.CacheDir}/${fileName}`;

    // Ensure previous file is deleted
    try { await ReactNativeBlobUtil.fs.unlink(cachePath); } catch (e) {}

    let lastProgress = 0;
    
    // Create the task
    const task = ReactNativeBlobUtil.config({
      path: cachePath,
      fileCache: true,
      trusty: true, // Bypass SSL validation if needed
    }).fetch('GET', rawUrl, {
      'Authorization': authHeader,
      'AList-Token': authHeader,
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
    });

    this.activeTasks.set(fileName, task);

    task.progress((received, total) => {
      const progress = Math.round((Number(received) / Number(total)) * 100);
      if (progress > lastProgress) {
        lastProgress = progress;
        DeviceEventEmitter.emit('onDownloadProgress', { fileName, progress });
      }
    });

    try {
      const res = await task;
      const status = res.info().status;
      
      if (status !== 200) {
        throw new Error(`Server returned HTTP ${status}`);
      }

      // Ensure file actually exists and isn't empty
      const stat = await ReactNativeBlobUtil.fs.stat(cachePath);
      if (stat.size === 0) {
          throw new Error('Downloaded file is empty (0 bytes).');
      }
      
      // Simple HTML check on file size to prevent 38KB HTML issue silently passing
      if (stat.size < 100 * 1024 && !mimeType.includes('html')) {
          const firstBytes = await ReactNativeBlobUtil.fs.readFile(cachePath, 'utf8');
          if (firstBytes.trim().startsWith('<') && firstBytes.toLowerCase().includes('<html')) {
              throw new Error('Server returned an HTML page instead of the file.');
          }
      }

      // Copy to SAF
      const safUri = await localFsService.copyFileToSAF(cachePath, localDirUri, fileName, mimeType || 'application/octet-stream');
      
      // Cleanup cache
      await ReactNativeBlobUtil.fs.unlink(cachePath);

      DeviceEventEmitter.emit('onDownloadComplete', { fileName, uri: safUri });
      this.activeTasks.delete(fileName);
      return true;

    } catch (e: any) {
      if (e.message && e.message.includes('cancel')) {
         DeviceEventEmitter.emit('onDownloadCancelled', { fileName });
      } else {
         DeviceEventEmitter.emit('onDownloadError', { fileName, error: e.message || 'Download failed' });
      }
      this.activeTasks.delete(fileName);
      try { await ReactNativeBlobUtil.fs.unlink(cachePath); } catch (err) {}
      return false;
    }
  }

  async cancelDownload(fileName: string): Promise<boolean> {
    const task = this.activeTasks.get(fileName);
    if (task) {
      task.cancel();
      this.activeTasks.delete(fileName);
      return true;
    }
    return false;
  }
}

export const downloadService = new DownloadService();
