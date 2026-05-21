import { NativeModules, NativeEventEmitter } from 'react-native';

interface ShareEventData {
  action: 'send' | 'send_multiple';
  mimeType: string;
  uri?: string;
  uris?: string[];
}

interface ShareModuleInterface {
  getPendingShare(): Promise<ShareEventData | null>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

const ShareNative: ShareModuleInterface = NativeModules.ShareModule;
const shareEmitter = new NativeEventEmitter(NativeModules.ShareModule);

async function getPendingShare(): Promise<ShareEventData | null> {
  return ShareNative.getPendingShare();
}

function onShareReceived(callback: (data: ShareEventData) => void) {
  return shareEmitter.addListener('onShareReceived', callback);
}

export { getPendingShare, onShareReceived };
export type { ShareEventData };
