import NativeSAF, { SAFDirResult, SAFFileInfo, SAFFileResult } from '../native/NativeSAF';

class LocalFsService {
  async pickDirectory(): Promise<SAFDirResult> {
    return NativeSAF.pickDirectory();
  }

  async pickFile(): Promise<SAFFileResult> {
    return NativeSAF.pickFile();
  }

  async listFiles(treeUri: string): Promise<SAFFileInfo[]> {
    return NativeSAF.listFiles(treeUri);
  }

  async readFile(uri: string): Promise<number[]> {
    return NativeSAF.readFile(uri);
  }

  async writeFile(treeUri: string, fileName: string, base64Data: string, mimeType: string): Promise<string> {
    return NativeSAF.writeFile(treeUri, fileName, base64Data, mimeType);
  }

  async downloadFileToSAF(url: string, treeUriStr: string, fileName: string, mimeType: string, headerAuth: string): Promise<string> {
    return NativeSAF.downloadFileToSAF(url, treeUriStr, fileName, mimeType, headerAuth);
  }

  async deleteFile(uri: string): Promise<boolean> {
    return NativeSAF.deleteFile(uri);
  }

  async getFileDetail(uri: string): Promise<{ name: string; size: number; uri: string }> {
    return NativeSAF.getFileDetail(uri);
  }
}

export const localFsService = new LocalFsService();
