import { NativeModules } from 'react-native';

interface SAFDirResult {
  uri: string;
  name: string;
}

interface SAFFileInfo {
  name: string;
  is_dir: boolean;
  size: number;
  modified: number;
  uri: string;
  mimeType: string;
  documentId: string;
}

interface SAFFileResult {
  uri: string;
  name: string;
  size: number;
  mimeType: string;
}

interface SAFModuleInterface {
  pickDirectory(): Promise<SAFDirResult>;
  pickFile(): Promise<SAFFileResult>;
  listFiles(treeUri: string): Promise<SAFFileInfo[]>;
  readFile(uri: string): Promise<number[]>;
  writeFile(treeUri: string, fileName: string, base64Data: string, mimeType: string): Promise<string>;
  downloadFileToSAF(url: String, treeUriStr: String, fileName: String, mimeType: String, headerAuth: String): Promise<string>;
  deleteFile(uri: string): Promise<boolean>;
  getFileDetail(uri: string): Promise<{ name: string; size: number; uri: string }>;
}

const SAFModule: SAFModuleInterface = NativeModules.SAFModule;

export default SAFModule;
export type { SAFDirResult, SAFFileInfo, SAFFileResult };
