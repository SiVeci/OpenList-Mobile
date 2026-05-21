import { AListService } from './alistService';
import { localFsService } from './localFsService';
import { ServerConfig } from '../types';
import NetInfo from '@react-native-community/netinfo';
import DeviceInfo from 'react-native-device-info';

export interface SyncRule {
  id: string;
  name: string;
  remotePath: string;
  localDirUri: string;
  localDirName: string;
  direction: 'remote_to_local' | 'local_to_remote' | 'bidirectional';
  conflictStrategy: 'skip' | 'newer_wins' | 'manual';
  enabled: boolean;
  lastSyncTime: number | null;
  intervalMinutes: number;
  wifiOnly: boolean;
  chargingOnly: boolean;
}

export interface SyncResult {
  ruleId: string;
  synced: number;
  skipped: number;
  conflicts: number;
  errors: string[];
  timestamp: number;
}

class SyncService {
  async checkConstraints(rule: SyncRule): Promise<boolean> {
    if (!rule.enabled) return false;
    
    if (rule.wifiOnly) {
      const netState = await NetInfo.fetch();
      if (netState.type !== 'wifi') {
        return false;
      }
    }
    
    if (rule.chargingOnly) {
      const isCharging = await DeviceInfo.isBatteryCharging();
      if (!isCharging) {
        return false;
      }
    }
    
    return true;
  }

  async executeSync(
    config: ServerConfig,
    rule: SyncRule,
    onProgress?: (current: number, total: number) => void
  ): Promise<SyncResult> {
    const meetsConstraints = await this.checkConstraints(rule);
    if (!meetsConstraints) {
      return {
        ruleId: rule.id,
        synced: 0,
        skipped: 0,
        conflicts: 0,
        errors: ['Constraints not met (WiFi/Charging)'],
        timestamp: Date.now()
      };
    }

    if (rule.direction === 'remote_to_local') {
      return this.syncRemoteToLocal(config, rule, onProgress);
    } else if (rule.direction === 'local_to_remote') {
      return this.syncLocalToRemote(config, rule, onProgress);
    } else {
      // bidirectional - simple implementation: remote->local then local->remote
      const res1 = await this.syncRemoteToLocal(config, rule, onProgress);
      const res2 = await this.syncLocalToRemote(config, rule, onProgress);
      return {
        ruleId: rule.id,
        synced: res1.synced + res2.synced,
        skipped: res1.skipped + res2.skipped,
        conflicts: res1.conflicts + res2.conflicts,
        errors: [...res1.errors, ...res2.errors],
        timestamp: Date.now()
      };
    }
  }

  async syncRemoteToLocal(
    config: ServerConfig,
    rule: SyncRule,
    onProgress?: (current: number, total: number) => void,
  ): Promise<SyncResult> {
    const service = new AListService(config);
    const result: SyncResult = {
      ruleId: rule.id,
      synced: 0,
      skipped: 0,
      conflicts: 0,
      errors: [],
      timestamp: Date.now(),
    };

    try {
      const remoteFiles = await service.listFiles(rule.remotePath, 1, 1000, true);
      const remoteContent = remoteFiles.data.content || [];
      const localFiles = await localFsService.listFiles(rule.localDirUri);

      const localFileMap = new Map<string, typeof localFiles[0]>();
      for (const lf of localFiles) {
        localFileMap.set(lf.name.toLowerCase(), lf);
      }

      const filesToSync = remoteContent.filter(f => !f.is_dir);

      for (let i = 0; i < filesToSync.length; i++) {
        const rf = filesToSync[i];
        onProgress?.(i + 1, filesToSync.length);

        const existingLocal = localFileMap.get(rf.name.toLowerCase());

        let skip = false;
        if (existingLocal) {
          if (rule.conflictStrategy === 'skip') {
            skip = true;
          } else if (rule.conflictStrategy === 'newer_wins') {
            const remoteTime = new Date(rf.modified).getTime();
            if (remoteTime <= existingLocal.modified) {
              skip = true;
            }
          } else {
            // default to simple size check for manual/other
            if (existingLocal.size === rf.size) {
              skip = true;
            }
          }
        }

        if (skip) {
          result.skipped++;
          continue;
        }

        try {
          const detail = await service.getFileDetail(
            (rule.remotePath === '/' ? '' : rule.remotePath) + '/' + rf.name
          );
          const rawUrl = detail.data.raw_url;

          const ext = rf.name.split('.').pop()?.toLowerCase() || '';
          const mimeType = this.getMimeType(ext);

          // If local file exists, we should delete it before downloading the new one
          // to avoid duplicate files in SAF (which might append (1) to the name)
          if (existingLocal) {
             await localFsService.deleteFile(existingLocal.uri);
          }

          await localFsService.downloadFileToSAF(rawUrl, rule.localDirUri, rf.name, mimeType, config.token);
          result.synced++;
        } catch (err: any) {
          result.errors.push(`${rf.name}: ${err.message}`);
        }
      }
    } catch (err: any) {
      result.errors.push(`Remote error: ${err.message}`);
    }

    return result;
  }

  async syncLocalToRemote(
    config: ServerConfig,
    rule: SyncRule,
    onProgress?: (current: number, total: number) => void,
  ): Promise<SyncResult> {
    const service = new AListService(config);
    const result: SyncResult = {
      ruleId: rule.id,
      synced: 0,
      skipped: 0,
      conflicts: 0,
      errors: [],
      timestamp: Date.now(),
    };

    try {
      const localFiles = await localFsService.listFiles(rule.localDirUri);
      const filesToSync = localFiles.filter(f => !f.is_dir);
      
      let remoteContent: any[] = [];
      try {
        const remoteFiles = await service.listFiles(rule.remotePath, 1, 1000, true);
        remoteContent = remoteFiles.data.content || [];
      } catch {
        // Directory might be empty or not exist, proceed with empty remote content
      }

      const remoteFileMap = new Map<string, any>();
      for (const rf of remoteContent) {
        if (!rf.is_dir) {
          remoteFileMap.set(rf.name.toLowerCase(), rf);
        }
      }

      for (let i = 0; i < filesToSync.length; i++) {
        const lf = filesToSync[i];
        onProgress?.(i + 1, filesToSync.length);

        const existingRemote = remoteFileMap.get(lf.name.toLowerCase());

        let skip = false;
        if (existingRemote) {
          if (rule.conflictStrategy === 'skip') {
            skip = true;
          } else if (rule.conflictStrategy === 'newer_wins') {
            const remoteTime = new Date(existingRemote.modified).getTime();
            if (lf.modified <= remoteTime) {
              skip = true;
            }
          } else {
            if (existingRemote.size === lf.size) {
              skip = true;
            }
          }
        }

        if (skip) {
          result.skipped++;
          continue;
        }

        try {
          await service.uploadFile(rule.remotePath, lf.uri, lf.name, lf.mimeType);
          result.synced++;
        } catch (err: any) {
          result.errors.push(`${lf.name}: ${err.message}`);
        }
      }
    } catch (err: any) {
      result.errors.push(`Local error: ${err.message}`);
    }

    return result;
  }

  private getMimeType(ext: string): string {
    const mimeMap: Record<string, string> = {
      jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', gif: 'image/gif', webp: 'image/webp',
      mp4: 'video/mp4', mkv: 'video/x-matroska', avi: 'video/x-msvideo', mov: 'video/quicktime',
      mp3: 'audio/mpeg', flac: 'audio/flac', wav: 'audio/wav',
      pdf: 'application/pdf', doc: 'application/msword', docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      zip: 'application/zip', rar: 'application/x-rar-compressed', '7z': 'application/x-7z-compressed',
      txt: 'text/plain', json: 'application/json', html: 'text/html', css: 'text/css', js: 'text/javascript',
    };
    return mimeMap[ext] || 'application/octet-stream';
  }
}

export const syncService = new SyncService();
