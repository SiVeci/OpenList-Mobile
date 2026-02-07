
import React, { useState, useEffect, useCallback } from 'react';
import { ServerConfig, AListFile } from './types';
import { AListService } from './services/alistService';
import FileBrowser from './components/FileBrowser';
import Login from './components/Login';
import { LogOut, RefreshCw, Smartphone, Server } from 'lucide-react';

const App: React.FC = () => {
  const [config, setConfig] = useState<ServerConfig | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load saved config on mount
  useEffect(() => {
    const saved = localStorage.getItem('alist_config');
    if (saved) {
      try {
        setConfig(JSON.parse(saved));
      } catch (e) {
        console.error("Failed to parse saved config");
      }
    }
    setIsLoading(false);
  }, []);

  const handleLogin = (newConfig: ServerConfig) => {
    setConfig(newConfig);
    localStorage.setItem('alist_config', JSON.stringify(newConfig));
  };

  const handleLogout = () => {
    setConfig(null);
    localStorage.removeItem('alist_config');
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#f7f2fa]">
        <RefreshCw className="w-8 h-8 text-indigo-600 animate-spin" />
      </div>
    );
  }

  if (!config) {
    return <Login onLogin={handleLogin} />;
  }

  return (
    <div className="min-h-screen flex flex-col bg-[#f7f2fa]">
      {/* App Bar - Added pt-safe for status bar avoidance */}
      <header className="sticky top-0 z-20 bg-white/80 backdrop-blur-md px-4 pt-safe flex flex-col border-b border-gray-100">
        <div className="py-3 flex items-center justify-between w-full">
          <div className="flex items-center gap-2">
            <Server className="w-6 h-6 text-indigo-600" />
            <h1 className="font-bold text-lg text-gray-800 truncate max-w-[200px]">
              {config.serverName || 'OpenList'}
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <button 
              onClick={handleLogout}
              className="p-2 hover:bg-red-50 text-red-500 rounded-full transition-colors"
              title="Logout"
            >
              <LogOut className="w-5 h-5" />
            </button>
          </div>
        </div>
      </header>

      <main className="flex-1 overflow-hidden">
        <FileBrowser config={config} onSessionExpired={handleLogout} />
      </main>

      {/* Global Status Banner for Errors */}
      {error && (
        <div className="fixed bottom-24 left-4 right-4 bg-red-600 text-white p-3 rounded-xl shadow-lg flex items-center justify-between z-50 animate-bounce pb-safe">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="ml-2 font-bold">X</button>
        </div>
      )}
    </div>
  );
};

export default App;
