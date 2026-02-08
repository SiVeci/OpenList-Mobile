
import React, { useState, useEffect, useRef } from 'react';
import { ServerConfig } from '../types';
import { AListService } from '../services/alistService';
import { 
  Server, User, Lock, ArrowRight, AlertTriangle, 
  ShieldCheck, HelpCircle, Globe, Hash, Info, 
  Wifi, Tag, X, History, Trash2, ChevronDown 
} from 'lucide-react';

interface Props {
  onLogin: (config: ServerConfig) => void;
}

const Login: React.FC<Props> = ({ onLogin }) => {
  const [serverName, setServerName] = useState('');
  const [protocol, setProtocol] = useState<'http://' | 'https://'>('http://');
  const [address, setAddress] = useState('');
  const [port, setPort] = useState('5244');
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<{ message: string, type?: 'mixed' | 'cors' | 'auth' | 'local' } | null>(null);
  const [isHttpsApp, setIsHttpsApp] = useState(false);
  const [savedConfigs, setSavedConfigs] = useState<ServerConfig[]>([]);
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [configToDelete, setConfigToDelete] = useState<{ index: number, name: string } | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setIsHttpsApp(window.location.protocol === 'https:');
    if (window.location.protocol === 'https:') {
      setProtocol('https://');
    }
    
    const history = localStorage.getItem('alist_login_history');
    if (history) {
      try {
        setSavedConfigs(JSON.parse(history));
      } catch (e) {
        console.error("Failed to load history");
      }
    }

    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const checkIsLocal = (addr: string) => {
    const lower = addr.toLowerCase().trim();
    if (!lower) return false;
    if (lower === 'localhost' || lower === '127.0.0.1' || lower.endsWith('.local')) return true;
    if (/^192\.168\./.test(lower)) return true;
    if (/^10\./.test(lower)) return true;
    const parts = lower.split('.');
    if (parts.length === 4 && parts[0] === '172') {
      const secondPart = parseInt(parts[1], 10);
      if (secondPart >= 16 && secondPart <= 31) return true;
    }
    return false;
  };

  const isLocal = checkIsLocal(address);
  const isMixedContent = isHttpsApp && protocol === 'http://' && !isLocal;

  const handleAddressChange = (val: string) => {
    let clean = val.trim();
    if (clean.toLowerCase().startsWith('https://')) {
      setProtocol('https://');
      clean = clean.substring(8);
    } else if (clean.toLowerCase().startsWith('http://')) {
      setProtocol('http://');
      clean = clean.substring(7);
    }
    if (clean.includes(':')) {
      const parts = clean.split(':');
      clean = parts[0];
      const possiblePort = parts[1].split('/')[0];
      if (possiblePort && !isNaN(parseInt(possiblePort))) {
        setPort(possiblePort);
      }
    }
    setAddress(clean);
  };

  const selectSavedConfig = (config: ServerConfig) => {
    setServerName(config.serverName);
    setUsername(config.username);
    try {
      const url = new URL(config.url);
      setProtocol(url.protocol === 'https:' ? 'https://' : 'http://');
      setAddress(url.hostname);
      setPort(url.port || (url.protocol === 'https:' ? '443' : '80'));
    } catch (e) {
      const isHttps = config.url.startsWith('https://');
      setProtocol(isHttps ? 'https://' : 'http://');
      const withoutProto = config.url.replace(/^https?:\/\//, '');
      const parts = withoutProto.split(':');
      setAddress(parts[0]);
      if (parts[1]) setPort(parts[1]);
    }
    setIsDropdownOpen(false);
  };

  const initiateDelete = (e: React.MouseEvent, index: number, name: string) => {
    e.stopPropagation();
    setConfigToDelete({ index, name });
  };

  const confirmDelete = () => {
    if (configToDelete === null) return;
    const updated = savedConfigs.filter((_, i) => i !== configToDelete.index);
    setSavedConfigs(updated);
    localStorage.setItem('alist_login_history', JSON.stringify(updated));
    setConfigToDelete(null);
    if (updated.length === 0) setIsDropdownOpen(false);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    const cleanAddress = address.trim().replace(/\/$/, "");
    const cleanPort = port.trim();
    const targetUrl = `${protocol}${cleanAddress}${cleanPort ? `:${cleanPort}` : ''}`;
    const finalServerName = serverName.trim() || 'OpenList';

    if (!cleanAddress) {
      setError({ message: "Please enter a server address." });
      setLoading(false);
      return;
    }

    try {
      const token = await AListService.login(targetUrl, username, password);
      const newConfig: ServerConfig = { url: targetUrl, username, token, serverName: finalServerName };
      
      const existing = savedConfigs.findIndex(c => c.url === targetUrl && c.username === username);
      let updatedHistory = [...savedConfigs];
      if (existing !== -1) {
        updatedHistory[existing] = newConfig;
      } else {
        updatedHistory.unshift(newConfig);
      }
      updatedHistory = updatedHistory.slice(0, 5);
      localStorage.setItem('alist_login_history', JSON.stringify(updatedHistory));
      
      onLogin(newConfig);
    } catch (err: any) {
      if (err.message === 'Failed to fetch' || err.name === 'TypeError') {
        if (isMixedContent) {
          setError({ 
            message: 'Mixed Content Blocked: HTTPS apps cannot connect to external HTTP servers.',
            type: 'mixed'
          });
        } else if (isLocal) {
          setError({ 
            message: 'Local connection failed. Check Wi-Fi and AList settings.',
            type: 'local'
          });
        } else {
          setError({ 
            message: 'Network unreachable. Verify the IP/Port.',
            type: 'cors'
          });
        }
      } else {
        setError({ message: err.message || 'Login failed.', type: 'auth' });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f7f2fa] flex items-center justify-center px-4 py-8">
      <div className="w-full max-w-md bg-white rounded-[32px] p-6 shadow-sm border border-gray-100 relative">
        <div className="flex flex-col items-center mb-5">
          <div className="w-14 h-14 bg-indigo-600 rounded-2xl flex items-center justify-center mb-3 shadow-lg shadow-indigo-100 transition-transform hover:scale-105 active:scale-95">
            <Server className="w-7 h-7 text-white" />
          </div>
          <h2 className="text-lg font-black text-gray-900 tracking-tight whitespace-nowrap">Remote Storage Manager</h2>
          <p className="text-[9px] text-gray-400 font-black mt-1 uppercase tracking-widest whitespace-nowrap">SUPPORT ALIST AND OPENLIST SERVERS</p>
        </div>

        {savedConfigs.length > 0 && (
          <div className="mb-5 relative" ref={dropdownRef}>
            <div className="flex items-center gap-1.5 px-1 mb-1.5">
              <History className="w-3 h-3 text-indigo-400" />
              <label className="text-[9px] font-bold text-gray-400 uppercase tracking-widest">Select From History</label>
            </div>
            
            <button
              type="button"
              onClick={() => setIsDropdownOpen(!isDropdownOpen)}
              className="w-full flex items-center justify-between px-4 py-3 bg-gray-50 border border-gray-100 rounded-xl hover:bg-gray-100 active:scale-[0.99] transition-all text-left"
            >
              <span className="text-xs font-bold text-gray-600 truncate">
                {serverName ? `Current: ${serverName}` : "Pick a saved server..."}
              </span>
              <ChevronDown className={`w-4 h-4 text-gray-400 transition-transform duration-200 ${isDropdownOpen ? 'rotate-180' : ''}`} />
            </button>

            {isDropdownOpen && (
              <div className="absolute top-full left-0 right-0 mt-2 bg-white border border-gray-100 rounded-2xl shadow-xl z-50 overflow-hidden animate-in fade-in slide-in-from-top-2 duration-200">
                <div className="max-h-48 overflow-y-auto hide-scrollbar">
                  {savedConfigs.map((cfg, idx) => (
                    <div 
                      key={idx}
                      onClick={() => selectSavedConfig(cfg)}
                      className="flex items-center justify-between p-3.5 hover:bg-indigo-50 border-b border-gray-50 last:border-0 cursor-pointer transition-colors group"
                    >
                      <div className="flex flex-col min-w-0">
                        <span className="text-[11px] font-bold text-gray-800 truncate">{cfg.serverName}</span>
                        <span className="text-[9px] text-gray-400 truncate opacity-70">{cfg.url}</span>
                      </div>
                      <button 
                        onClick={(e) => initiateDelete(e, idx, cfg.serverName)}
                        className="p-2 hover:bg-red-100 rounded-lg text-gray-300 group-hover:text-red-400 transition-colors"
                        title="Delete from history"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="space-y-1.5">
            <label className="text-[9px] font-bold text-gray-400 uppercase tracking-widest px-1">General Info</label>
            <div className="relative">
              <Tag className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-300" />
              <input
                type="text"
                placeholder="Server Nickname"
                className="w-full pl-11 pr-4 py-2.5 bg-gray-50 border border-gray-100 rounded-xl focus:ring-2 focus:ring-indigo-500 outline-none font-medium text-gray-800 text-sm"
                value={serverName}
                onChange={(e) => setServerName(e.target.value)}
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <div className="flex justify-between items-center px-1">
              <label className="text-[9px] font-bold text-gray-400 uppercase tracking-widest">Server Config</label>
              <div className="flex items-center gap-1.5">
                {isLocal ? (
                  <span className="text-[8px] text-blue-600 font-bold bg-blue-50 px-1.5 py-0.5 rounded-full flex items-center gap-1">
                    <Wifi className="w-2 h-2"/> Local
                  </span>
                ) : protocol === 'https://' ? (
                  <span className="text-[8px] text-green-600 font-bold bg-green-50 px-1.5 py-0.5 rounded-full flex items-center gap-1">
                    <ShieldCheck className="w-2 h-2"/> SSL
                  </span>
                ) : (
                  <span className="text-[8px] text-orange-600 font-bold bg-orange-50 px-1.5 py-0.5 rounded-full flex items-center gap-1">
                    <AlertTriangle className="w-2 h-2"/> No SSL
                  </span>
                )}
              </div>
            </div>

            <div className="bg-gray-50/50 p-1.5 rounded-2xl border border-gray-100 space-y-1.5">
              <div className="flex gap-2">
                <div className="flex bg-gray-200/50 p-0.5 rounded-lg w-24 shrink-0">
                  <button
                    type="button"
                    onClick={() => setProtocol('http://')}
                    className={`flex-1 text-[8px] font-black py-1 rounded-md transition-all ${protocol === 'http://' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-400'}`}
                  >
                    HTTP
                  </button>
                  <button
                    type="button"
                    onClick={() => setProtocol('https://')}
                    className={`flex-1 text-[8px] font-black py-1 rounded-md transition-all ${protocol === 'https://' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-400'}`}
                  >
                    HTTPS
                  </button>
                </div>

                <div className="relative flex-1">
                  <Hash className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
                  <input
                    type="number"
                    placeholder="Port"
                    className="w-full pl-8 pr-3 py-1.5 bg-white border border-gray-100 rounded-lg focus:ring-2 focus:ring-indigo-500 outline-none text-xs font-mono"
                    value={port}
                    onChange={(e) => setPort(e.target.value)}
                  />
                </div>
              </div>

              <div className="relative w-full">
                <Globe className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-300" />
                <input
                  type="text"
                  required
                  placeholder="Address (IP or Domain)"
                  className="w-full pl-11 pr-4 py-2.5 bg-white border border-gray-100 rounded-xl focus:ring-2 focus:ring-indigo-500 outline-none font-semibold text-gray-800 text-sm"
                  value={address}
                  onChange={(e) => handleAddressChange(e.target.value)}
                />
              </div>
            </div>
          </div>

          <div className="space-y-1.5">
            <div className="relative">
              <User className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-300" />
              <input
                type="text"
                required
                placeholder="Username"
                className="w-full pl-11 pr-4 py-2.5 bg-gray-50 border border-gray-100 rounded-xl focus:ring-2 focus:ring-indigo-500 outline-none font-medium text-sm"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>

            <div className="relative">
              <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-300" />
              <input
                type="password"
                required
                placeholder="Password"
                className="w-full pl-11 pr-4 py-2.5 bg-gray-50 border border-gray-100 rounded-xl focus:ring-2 focus:ring-indigo-500 outline-none font-medium text-sm"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          {error && (
            <div className="p-2.5 bg-red-50 text-red-700 text-[9px] rounded-xl border border-red-100 flex gap-2 animate-shake">
              <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
              <div className="font-semibold leading-relaxed">{error.message}</div>
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-indigo-600 text-white py-3.5 rounded-2xl font-bold hover:bg-indigo-700 transition-all active:scale-[0.97] disabled:opacity-70 flex items-center justify-center gap-2 shadow-lg shadow-indigo-100 mt-1"
          >
            {loading ? (
              <RefreshCw className="w-5 h-5 animate-spin" />
            ) : (
              <>
                <span className="text-base">Connect Now</span>
                <ArrowRight className="w-5 h-5" />
              </>
            )}
          </button>
        </form>

        <p className="text-center text-[9px] text-gray-300 mt-5 font-bold uppercase tracking-[0.2em]">
          ·V0.1.2
        </p>

        {/* Delete Confirmation Overlay */}
        {configToDelete && (
          <div className="absolute inset-0 z-[60] flex items-center justify-center p-6 animate-in fade-in duration-200">
            <div className="absolute inset-0 bg-white/90 backdrop-blur-sm rounded-[32px]" />
            <div className="relative bg-white border border-gray-100 rounded-3xl p-6 shadow-2xl w-full max-w-xs animate-in zoom-in-95 duration-200">
              <div className="flex flex-col items-center text-center">
                <div className="w-12 h-12 bg-red-50 rounded-2xl flex items-center justify-center mb-4">
                  <Trash2 className="w-6 h-6 text-red-500" />
                </div>
                <h3 className="text-sm font-black text-gray-900 mb-2">Delete Saved Config?</h3>
                <p className="text-[11px] text-gray-500 leading-relaxed mb-6">
                  Are you sure you want to remove <strong>{configToDelete.name}</strong> from your history?
                </p>
                <div className="grid grid-cols-2 gap-3 w-full">
                  <button
                    onClick={() => setConfigToDelete(null)}
                    className="py-2.5 bg-gray-50 text-gray-600 rounded-xl text-[11px] font-bold border border-gray-100 active:scale-95 transition-all"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={confirmDelete}
                    className="py-2.5 bg-red-600 text-white rounded-xl text-[11px] font-bold shadow-lg shadow-red-100 active:scale-95 transition-all"
                  >
                    Delete
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
      
      <style>{`
        @keyframes shake {
          0%, 100% { transform: translateX(0); }
          25% { transform: translateX(-4px); }
          75% { transform: translateX(4px); }
        }
        .animate-shake { animation: shake 0.2s ease-in-out 0s 2; }
      `}</style>
    </div>
  );
};

const RefreshCw: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M3 21v-5h5"/></svg>
);

export default Login;
