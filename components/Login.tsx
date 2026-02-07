
import React, { useState, useEffect } from 'react';
import { ServerConfig } from '../types';
import { AListService } from '../services/alistService';
import { Server, User, Lock, ArrowRight, AlertTriangle, ShieldCheck, HelpCircle, Globe, Hash, Info, Wifi, Tag } from 'lucide-react';

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

  useEffect(() => {
    setIsHttpsApp(window.location.protocol === 'https:');
    if (window.location.protocol === 'https:') {
      setProtocol('https://');
    }
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
      onLogin({ url: targetUrl, username, token, serverName: finalServerName });
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
      <div className="w-full max-w-md bg-white rounded-[32px] p-6 shadow-sm border border-gray-100">
        <div className="flex flex-col items-center mb-6">
          <div className="w-16 h-16 bg-indigo-600 rounded-2xl flex items-center justify-center mb-3 shadow-lg shadow-indigo-100 transition-transform hover:scale-105 active:scale-95">
            <Server className="w-8 h-8 text-white" />
          </div>
          <h2 className="text-xl font-black text-gray-900 tracking-tight whitespace-nowrap">Remote Storage Manager</h2>
          <p className="text-[10px] text-gray-400 font-black mt-1 uppercase tracking-widest whitespace-nowrap">SUPPORT ALIST AND OPENLIST SERVERS</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest px-1">General Info</label>
            <div className="relative">
              <Tag className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-300" />
              <input
                type="text"
                placeholder="Server Nickname"
                className="w-full pl-11 pr-4 py-3 bg-gray-50 border border-gray-100 rounded-xl focus:ring-2 focus:ring-indigo-500 outline-none font-medium text-gray-800 text-sm"
                value={serverName}
                onChange={(e) => setServerName(e.target.value)}
              />
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex justify-between items-center px-1">
              <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">Server Config</label>
              <div className="flex items-center gap-1.5">
                {isLocal ? (
                  <span className="text-[9px] text-blue-600 font-bold bg-blue-50 px-1.5 py-0.5 rounded-full flex items-center gap-1">
                    <Wifi className="w-2.5 h-2.5"/> Local
                  </span>
                ) : protocol === 'https://' ? (
                  <span className="text-[9px] text-green-600 font-bold bg-green-50 px-1.5 py-0.5 rounded-full flex items-center gap-1">
                    <ShieldCheck className="w-2.5 h-2.5"/> SSL
                  </span>
                ) : (
                  <span className="text-[9px] text-orange-600 font-bold bg-orange-50 px-1.5 py-0.5 rounded-full flex items-center gap-1">
                    <AlertTriangle className="w-2.5 h-2.5"/> No SSL
                  </span>
                )}
              </div>
            </div>

            <div className="bg-gray-50/50 p-2 rounded-2xl border border-gray-100 space-y-2">
              <div className="flex gap-2">
                <div className="flex bg-gray-200/50 p-1 rounded-xl w-28 shrink-0">
                  <button
                    type="button"
                    onClick={() => setProtocol('http://')}
                    className={`flex-1 text-[9px] font-black py-1.5 rounded-lg transition-all ${protocol === 'http://' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-400'}`}
                  >
                    HTTP
                  </button>
                  <button
                    type="button"
                    onClick={() => setProtocol('https://')}
                    className={`flex-1 text-[9px] font-black py-1.5 rounded-lg transition-all ${protocol === 'https://' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-400'}`}
                  >
                    HTTPS
                  </button>
                </div>

                <div className="relative flex-1">
                  <Hash className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
                  <input
                    type="number"
                    placeholder="Port"
                    className="w-full pl-8 pr-3 py-2 bg-white border border-gray-100 rounded-xl focus:ring-2 focus:ring-indigo-500 outline-none text-xs font-mono"
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
                  className="w-full pl-11 pr-4 py-3 bg-white border border-gray-100 rounded-xl focus:ring-2 focus:ring-indigo-500 outline-none font-semibold text-gray-800 text-sm"
                  value={address}
                  onChange={(e) => handleAddressChange(e.target.value)}
                />
              </div>
            </div>
          </div>

          <div className="space-y-2">
            <div className="relative">
              <User className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-300" />
              <input
                type="text"
                required
                placeholder="Username"
                className="w-full pl-11 pr-4 py-3 bg-gray-50 border border-gray-100 rounded-xl focus:ring-2 focus:ring-indigo-500 outline-none font-medium text-sm"
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
                className="w-full pl-11 pr-4 py-3 bg-gray-50 border border-gray-100 rounded-xl focus:ring-2 focus:ring-indigo-500 outline-none font-medium text-sm"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          {error && (
            <div className="p-3 bg-red-50 text-red-700 text-[10px] rounded-xl border border-red-100 flex gap-2 animate-shake">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <div className="font-semibold leading-relaxed">{error.message}</div>
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-indigo-600 text-white py-4 rounded-2xl font-bold hover:bg-indigo-700 transition-all active:scale-[0.97] disabled:opacity-70 flex items-center justify-center gap-2 shadow-lg shadow-indigo-100 mt-2"
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

        <p className="text-center text-[10px] text-gray-300 mt-6 font-bold uppercase tracking-[0.2em]">
          ·V0.1.2
        </p>
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
