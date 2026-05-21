import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { SyncRule } from '../services/syncService';

interface DownloadState {
  activeDownloads: Record<string, { fileName: string; progress: number; status: string; uri?: string }>;
  syncRules: SyncRule[];
  addSyncRule: (rule: SyncRule) => void;
  updateSyncRule: (id: string, updates: Partial<SyncRule>) => void;
  deleteSyncRule: (id: string) => void;
  setLastSyncTime: (id: string, time: number) => void;
  updateDownload: (fileName: string, data: { progress: number; status: string; uri?: string }) => void;
  clearDownloads: () => void;
}

export const useAppStore = create<DownloadState>()(
  persist(
    (set, get) => ({
      activeDownloads: {},
      syncRules: [],

      updateDownload: (fileName: string, data: { progress: number; status: string; uri?: string }) => {
        set(state => ({
          activeDownloads: {
            ...state.activeDownloads,
            [fileName]: { fileName, ...data },
          }
        }));
      },

      clearDownloads: () => {
        set({ activeDownloads: {} });
      },

      addSyncRule: (rule: SyncRule) => {
        set({ syncRules: [...get().syncRules, rule] });
      },

      updateSyncRule: (id: string, updates: Partial<SyncRule>) => {
        set({
          syncRules: get().syncRules.map(r =>
            r.id === id ? { ...r, ...updates } : r
          ),
        });
      },

      deleteSyncRule: (id: string) => {
        set({ syncRules: get().syncRules.filter(r => r.id !== id) });
      },

      setLastSyncTime: (id: string, time: number) => {
        set({
          syncRules: get().syncRules.map(r =>
            r.id === id ? { ...r, lastSyncTime: time } : r
          ),
        });
      },
    }),
    {
      name: 'openlist-app-storage',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({
        syncRules: state.syncRules,
      }),
    }
  )
);
