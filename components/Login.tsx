
import React, { useState, useEffect } from 'react';
import { ServerConfig } from '../types';
import { AListService } from '../services/alistService';
import { Server, User, Lock, ArrowRight, AlertTriangle, ShieldCheck, HelpCircle, Globe, Hash } from 'lucide-react';

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
  const [error, setError] = useState<string | null>(null);
  const [isHttpsApp, setIsHttpsApp] = useState(false);

  useEffect(() => {
    setIsHttpsApp(window.location.protocol === 'https:');
    // Default to HTTPS if the app itself is HTTPS
    if (window.location.protocol === 'https:') {
      setProtocol('https://');
    }
  }, []);

  const isMixedContent = isHttpsApp && protocol === 'http://';

  const handleAddressChange = (val: string) => {
    // Strip common protocol prefixes if user pastes a full URL
    let clean = val.trim();
    if (clean.toLowerCase().startsWith('https://')) {
      setProtocol('https://');
      clean = clean.substring(8);
    } else if (clean.toLowerCase().startsWith('http://')) {
      setProtocol('http://');
      clean = clean.substring(7);
    }
    
    // Also handle trailing slashes and port numbers if pasted
    if (clean.includes(':')) {
      const parts = clean.split(':');
      clean = parts[0];
      const possiblePort = parts[1].split('/')[0];
      if (possiblePort) setPort(possiblePort);
    }
    
    setAddress(clean);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    // Construct full URL
    const cleanAddress = address.trim().replace(/\/$/, "");
    const cleanPort = port.trim();
    const targetUrl = `${protocol}${cleanAddress}${cleanPort ? `:${cleanPort}` : ''}`;

    if (!cleanAddress) {
      setError("Please enter a server address.");
      setLoading(false);
      return;
    }

    try {
      const token = await AListService.login(targetUrl, username, password);
      onLogin({ url: targetUrl, username, token });
    } catch (err: any) {
      console.error('Login Error details:', err);
      
      if (err.message === 'Failed to fetch' || err.name === 'TypeError') {
        if (isMixedContent) {
          setError('Mixed Content Error: Browsers block HTTP requests from an HTTPS app. Please use HTTPS for your AList server or access this app via an HTTP URL.');
        } else {
          setError('Network Error: Could not reach the server. Verify the address/port and ensure CORS is enabled in AList settings.');
        }
      } else {
        setError(err.message || 'Login failed.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f7f2fa] flex items-center justify-center px-6">
      <div className="w-full max-w-md bg-white rounded-[32px] p-8 shadow-sm border border-gray-100">
        <div className="flex flex-col items-center mb-8">
          <div className="w-20 h-20 bg-indigo-600 rounded-3xl flex items-center justify-center mb-4 shadow-lg shadow-indigo-100">
            <Server className="w-10 h-10 text-white" />
          </div>
          <h2 className="text-2xl font-bold text-gray-900">Connect AList</h2>
          <p className="text-gray-500 text-sm mt-1">Cloud storage at your fingertips</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Three-Part URL Input */}
          <div className="space-y-2">
            <label className="block text-xs font-bold text-gray-400 ml-1 uppercase tracking-widest flex justify-between">
              Server Configuration
              {protocol === 'https://' ? (
                <span className="text-green-600 flex items-center gap-1 normal-case font-semibold bg-green-50 px-2 rounded-full">
                  <ShieldCheck className="w-3 h-3"/> Secure Connection
                </span>
              ) : (
                <span className="text-orange-600 flex items-center gap-1 normal-case font-semibold bg-orange-50 px-2 rounded-full">
                  <AlertTriangle className="w-3 h-3"/> Insecure (Plain Text)
                </span>
              )}
            </label>
            
            <div className="flex flex-col gap-2">
              <div className="flex gap-2">
                {/* Protocol Toggle */}
                <div className="flex bg-gray-100 p-1 rounded-2xl w-32 shrink-0">
                  <button
                    type="button"
                    onClick={() => setProtocol('http://')}
                    className={`flex-1 text-[10px] font-bold py-2 rounded-xl transition-all ${protocol === 'http://' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-500'}`}
                  >
                    HTTP
                  </button>
                  <button
                    type="button"
                    onClick={() => setProtocol('https://')}
                    className={`flex-1 text-[10px] font-bold py-2 rounded-xl transition-all ${protocol === 'https://' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-500'}`}
                  >
                    HTTPS
                  </button>
                </div>

                {/* Port Input */}
                <div className="relative flex-1">
                  <Hash className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                  <input
                    type="number"
                    placeholder="Port (5244)"
                    className="w-full pl-9 pr-4 py-3 bg-gray-50 border border-gray-200 rounded-2xl focus:ring-2 focus:ring-indigo-500 outline-none text-sm font-mono"
                    value={port}
                    onChange={(e) => setPort(e.target.value)}
                  />
                </div>
              </div>

              {/* Address Input */}
              <div className="relative w-full">
                <Globe className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
                <input
                  type="text"
                  required
                  placeholder="e.g. 192.168.1.100 or alist.com"
                  className={`w-full pl-11 pr-4 py-4 bg-gray-50 border rounded-2xl focus:ring-2 transition-all outline-none font-medium ${
                    isMixedContent ? 'border-orange-300 focus:ring-orange-500' : 'border-gray-200 focus:ring-indigo-500'
                  }`}
                  value={address}
                  onChange={(e) => handleAddressChange(e.target.value)}
                />
              </div>
            </div>

            {isMixedContent && (
              <div className="p-3 bg-orange-50 border border-orange-100 rounded-2xl flex gap-3 items-start animate-in fade-in slide-in-from-top-2">
                <AlertTriangle className="w-5 h-5 text-orange-600 shrink-0 mt-0.5" />
                <p className="text-[11px] text-orange-800 leading-tight">
                  <strong>Protocol Mismatch:</strong> You are accessing an HTTP server from an HTTPS app. This is usually blocked by browsers. Try using HTTPS or an IP address with a local HTTP host.
                </p>
              </div>
            )}
          </div>

          <div className="space-y-4">
            <div className="relative">
              <User className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
              <input
                type="text"
                required
                placeholder="Username"
                className="w-full pl-12 pr-4 py-4 bg-gray-50 border border-gray-200 rounded-2xl focus:ring-2 focus:ring-indigo-500 outline-none"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>

            <div className="relative">
              <Lock className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
              <input
                type="password"
                required
                placeholder="Password"
                className="w-full pl-12 pr-4 py-4 bg-gray-50 border border-gray-200 rounded-2xl focus:ring-2 focus:ring-indigo-500 outline-none"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          {error && (
            <div className="p-4 bg-red-50 text-red-700 text-xs rounded-2xl border border-red-100 flex gap-3 animate-shake">
              <AlertTriangle className="w-5 h-5 shrink-0" />
              <div>
                <div className="font-bold mb-1">Configuration Error</div>
                {error}
              </div>
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-indigo-600 text-white py-4 rounded-2xl font-bold hover:bg-indigo-700 transition-all active:scale-[0.98] disabled:opacity-70 flex items-center justify-center gap-2 shadow-lg shadow-indigo-100 mt-2"
          >
            {loading ? (
              <RefreshCw className="w-6 h-6 animate-spin" />
            ) : (
              <>
                Connect to Server
                <ArrowRight className="w-6 h-6" />
              </>
            )}
          </button>
        </form>

        <div className="mt-8 flex items-center justify-center gap-2 opacity-30 grayscale pointer-events-none">
           <div className="w-10 h-1px bg-gray-400"></div>
           <span className="text-[10px] font-bold tracking-tighter uppercase">Device Local Auth</span>
           <div className="w-10 h-1px bg-gray-400"></div>
        </div>
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
  <svg className={className} xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M3 21v-5h5"/></svg>
);

export default Login;
