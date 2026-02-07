
import React, { useState, useEffect } from 'react';
import { ServerConfig } from '../types';
import { AListService } from '../services/alistService';
import { Server, User, Lock, ArrowRight, AlertTriangle, ShieldCheck, HelpCircle, Globe, Hash, Info, Wifi } from 'lucide-react';

interface Props {
  onLogin: (config: ServerConfig) => void;
}

const Login: React.FC<Props> = ({ onLogin }) => {
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

  // Helper to detect local network addresses
  const checkIsLocal = (addr: string) => {
    const lower = addr.toLowerCase().trim();
    if (!lower) return false;
    
    // Exact matches
    if (lower === 'localhost' || lower === '127.0.0.1' || lower.endsWith('.local')) return true;
    
    // IPv4 Private Ranges (RFC 1918)
    // 192.168.x.x
    if (/^192\.168\./.test(lower)) return true;
    // 10.x.x.x
    if (/^10\./.test(lower)) return true;
    // 172.16.x.x - 172.31.x.x
    const parts = lower.split('.');
    if (parts.length === 4 && parts[0] === '172') {
      const secondPart = parseInt(parts[1], 10);
      if (secondPart >= 16 && secondPart <= 31) return true;
    }
    
    return false;
  };

  const isLocal = checkIsLocal(address);
  // We still warn about Mixed Content for local IPs if the app is HTTPS, 
  // but we provide better messaging as browsers often treat local IPs differently.
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

    if (!cleanAddress) {
      setError({ message: "Please enter a server address." });
      setLoading(false);
      return;
    }

    try {
      const token = await AListService.login(targetUrl, username, password);
      onLogin({ url: targetUrl, username, token });
    } catch (err: any) {
      console.error('Detailed Connection Error:', err);
      
      if (err.message === 'Failed to fetch' || err.name === 'TypeError') {
        if (isMixedContent) {
          setError({ 
            message: 'Mixed Content Blocked: HTTPS apps cannot connect to external HTTP servers. Please use HTTPS or access this app via an HTTP connection.',
            type: 'mixed'
          });
        } else if (isLocal) {
          setError({ 
            message: 'Local connection failed. Please ensure your phone is connected to the same Wi-Fi as your AList server and that "Allow Origins: *" is set in AList settings.',
            type: 'local'
          });
        } else {
          setError({ 
            message: 'Network unreachable. Verify the IP/Port and check if AList is running.',
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
    <div className="min-h-screen bg-[#f7f2fa] flex items-center justify-center px-6">
      <div className="w-full max-w-md bg-white rounded-[40px] p-8 shadow-sm border border-gray-100">
        <div className="flex flex-col items-center mb-10">
          <div className="w-20 h-20 bg-indigo-600 rounded-[2rem] flex items-center justify-center mb-4 shadow-xl shadow-indigo-100 rotate-3 transition-transform hover:rotate-0">
            <Server className="w-10 h-10 text-white -rotate-3" />
          </div>
          <h2 className="text-3xl font-black text-gray-900 tracking-tight">OpenList</h2>
          <p className="text-gray-400 text-sm font-medium mt-1">Connect to your storage</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="space-y-3">
            <div className="flex justify-between items-center px-1">
              <label className="text-[11px] font-bold text-gray-400 uppercase tracking-widest">Server Config</label>
              <div className="flex items-center gap-1.5">
                {isLocal ? (
                  <span className="text-[10px] text-blue-600 font-bold bg-blue-50 px-2 py-0.5 rounded-full flex items-center gap-1">
                    <Wifi className="w-3 h-3"/> Local Network
                  </span>
                ) : protocol === 'https://' ? (
                  <span className="text-[10px] text-green-600 font-bold bg-green-50 px-2 py-0.5 rounded-full flex items-center gap-1">
                    <ShieldCheck className="w-3 h-3"/> SSL Secure
                  </span>
                ) : (
                  <span className="text-[10px] text-orange-600 font-bold bg-orange-50 px-2 py-0.5 rounded-full flex items-center gap-1">
                    <AlertTriangle className="w-3 h-3"/> No SSL
                  </span>
                )}
              </div>
            </div>

            <div className="bg-gray-50/50 p-2 rounded-[2rem] border border-gray-100 space-y-2">
              <div className="flex gap-2">
                <div className="flex bg-gray-200/50 p-1 rounded-2xl w-32 shrink-0">
                  <button
                    type="button"
                    onClick={() => setProtocol('http://')}
                    className={`flex-1 text-[10px] font-black py-2.5 rounded-xl transition-all ${protocol === 'http://' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-400'}`}
                  >
                    HTTP
                  </button>
                  <button
                    type="button"
                    onClick={() => setProtocol('https://')}
                    className={`flex-1 text-[10px] font-black py-2.5 rounded-xl transition-all ${protocol === 'https://' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-400'}`}
                  >
                    HTTPS
                  </button>
                </div>

                <div className="relative flex-1">
                  <Hash className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-300" />
                  <input
                    type="number"
                    placeholder="Port"
                    className="w-full pl-9 pr-4 py-3 bg-white border border-gray-100 rounded-2xl focus:ring-2 focus:ring-indigo-500 outline-none text-sm font-mono"
                    value={port}
                    onChange={(e) => setPort(e.target.value)}
                  />
                </div>
              </div>

              <div className="relative w-full">
                <Globe className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-300" />
                <input
                  type="text"
                  required
                  placeholder="Local IP or Domain"
                  className="w-full pl-12 pr-4 py-4 bg-white border border-gray-100 rounded-2xl focus:ring-2 focus:ring-indigo-500 outline-none font-semibold text-gray-800 transition-all"
                  value={address}
                  onChange={(e) => handleAddressChange(e.target.value)}
                />
              </div>
            </div>

            {isMixedContent && (
              <div className="p-4 bg-orange-50 border border-orange-100 rounded-3xl flex gap-3 animate-in slide-in-from-bottom-2">
                <Info className="w-5 h-5 text-orange-600 shrink-0 mt-0.5" />
                <div className="space-y-1">
                  <p className="text-[11px] text-orange-800 font-bold leading-tight">SECURITY NOTICE</p>
                  <p className="text-[11px] text-orange-700 leading-snug">
                    HTTPS apps are blocked from connecting to remote HTTP servers. Since this is not a local IP, please use HTTPS or switch to an HTTP host.
                  </p>
                </div>
              </div>
            )}
          </div>

          <div className="space-y-3">
            <div className="relative">
              <User className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-300" />
              <input
                type="text"
                required
                placeholder="Username"
                className="w-full pl-12 pr-4 py-4 bg-gray-50 border border-gray-100 rounded-2xl focus:ring-2 focus:ring-indigo-500 outline-none font-medium"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>

            <div className="relative">
              <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-300" />
              <input
                type="password"
                required
                placeholder="Password"
                className="w-full pl-12 pr-4 py-4 bg-gray-50 border border-gray-100 rounded-2xl focus:ring-2 focus:ring-indigo-500 outline-none font-medium"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          {error && (
            <div className="p-4 bg-red-50 text-red-700 text-[11px] rounded-3xl border border-red-100 space-y-3 animate-shake">
              <div className="flex gap-3">
                <AlertTriangle className="w-5 h-5 shrink-0" />
                <div className="font-semibold leading-relaxed">{error.message}</div>
              </div>
              
              {(error.type === 'mixed' || error.type === 'local' || error.type === 'cors') && (
                <div className="bg-white/50 p-3 rounded-2xl border border-red-100/50 space-y-2">
                  <p className="font-bold text-red-800 flex items-center gap-1 uppercase tracking-tighter">
                    <HelpCircle className="w-3 h-3" /> Possible Fixes:
                  </p>
                  <ul className="list-disc list-inside space-y-1 text-red-600">
                    {error.type === 'local' ? (
                      <>
                        <li>Ensure Wi-Fi is on and connected</li>
                        <li>Check if server IP <b>{address}</b> is reachable</li>
                        <li>Set <b>Allow Origins: *</b> in AList Settings</li>
                      </>
                    ) : (
                      <>
                        <li>Access app via <b>HTTP</b> instead of HTTPS</li>
                        <li>Setup <b>SSL</b> on your AList server</li>
                      </>
                    )}
                  </ul>
                </div>
              )}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-indigo-600 text-white py-5 rounded-[2rem] font-bold hover:bg-indigo-700 transition-all active:scale-[0.97] disabled:opacity-70 flex items-center justify-center gap-3 shadow-xl shadow-indigo-100"
          >
            {loading ? (
              <RefreshCw className="w-6 h-6 animate-spin" />
            ) : (
              <>
                <span className="text-lg">Log In</span>
                <ArrowRight className="w-6 h-6" />
              </>
            )}
          </button>
        </form>

        <p className="text-center text-[10px] text-gray-300 mt-10 font-bold uppercase tracking-[0.2em]">
          SUPPORT ALIST AND OPENLIST SERVERS
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
