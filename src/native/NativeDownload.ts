import { NativeModules, NativeEventEmitter } from 'react-native';

interface DownloadEventProgress {
  fileName: string;
  progress: number;
  bytesRead: number;
  totalBytes: number;
}

interface DownloadEventComplete {
  fileName: string;
  uri: string;
  progress: number;
}

interface DownloadEventError {
  fileName: string;
  error: string;
}

interface DownloadModuleInterface {
  startDownload(
    url: string,
    treeUri: string,
    fileName: string,
    fileSize: number,
    mimeType: string,
    authHeader: string
  ): Promise<boolean>;
  cancelDownload(fileName: string): Promise<boolean>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

const DownloadNative: DownloadModuleInterface = NativeModules.DownloadModule;

const downloadEmitter = new NativeEventEmitter(NativeModules.DownloadModule);

function onProgress(callback: (data: DownloadEventProgress) => void) {
  return downloadEmitter.addListener('onDownloadProgress', callback);
}

function onComplete(callback: (data: DownloadEventComplete) => void) {
  return downloadEmitter.addListener('onDownloadComplete', callback);
}

function onError(callback: (data: DownloadEventError) => void) {
  return downloadEmitter.addListener('onDownloadError', callback);
}

function onCancelled(callback: (data: { fileName: string }) => void) {
  return downloadEmitter.addListener('onDownloadCancelled', callback);
}

export default DownloadNative;
export { onProgress, onComplete, onError, onCancelled };
export type { DownloadEventProgress, DownloadEventComplete, DownloadEventError };
