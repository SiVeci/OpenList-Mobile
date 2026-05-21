import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ServerConfig } from '../types';
import { AListService } from '../services/alistService';

interface AuthState {
  config: ServerConfig | null;
  savedConfigs: ServerConfig[];
  isLoading: boolean;
  error: string | null;
  login: (url: string, username: string, password: string, serverName: string) => Promise<void>;
  logout: () => void;
  deleteSavedConfig: (index: number) => void;
  clearError: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      config: null,
      savedConfigs: [],
      isLoading: false,
      error: null,

      login: async (url: string, username: string, password: string, serverName: string) => {
        set({ isLoading: true, error: null });
        try {
          const token = await AListService.login(url, username, password);
          const newConfig: ServerConfig = { url, username, token, serverName: serverName || 'Server' };

          let updatedHistory = [...get().savedConfigs];
          if (serverName) {
            const existing = updatedHistory.findIndex(c => c.url === url && c.username === username);
            if (existing !== -1) {
              updatedHistory[existing] = newConfig;
            } else {
              updatedHistory.unshift(newConfig);
            }
            updatedHistory = updatedHistory.slice(0, 5);
          }

          set({ config: newConfig, savedConfigs: updatedHistory, isLoading: false });
        } catch (err: any) {
          let message = err.message || 'Login failed';
          if (err.message === 'Network Error' || err.code === 'ERR_NETWORK') {
            message = 'Network unreachable. Verify the IP/Port.';
          }
          set({ error: message, isLoading: false });
          throw err;
        }
      },

      logout: () => set({ config: null, error: null }),

      deleteSavedConfig: (index: number) => {
        const updated = get().savedConfigs.filter((_, i) => i !== index);
        set({ savedConfigs: updated });
      },

      clearError: () => set({ error: null }),
    }),
    {
      name: 'alist-auth-storage',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({
        config: state.config,
        savedConfigs: state.savedConfigs,
      }),
    }
  )
);
