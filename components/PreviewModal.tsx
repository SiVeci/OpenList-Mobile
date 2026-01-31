
import React, { useState, useEffect } from 'react';
import { X, Download, RefreshCw, AlertCircle, ExternalLink, FileText, Maximize2 } from 'lucide-react';
import { AListFile, ServerConfig } from '../types';

interface Props {
  file: AListFile;
  url: string;
  config: ServerConfig;
  type: 'image' | 'text' | 'pdf';
  onClose: () => void;
}

const PreviewModal: React.FC<Props> = ({ file, url, config, type, onClose }) => {
  const [textContent, setTextContent] = useState<string>('');
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let currentBlobUrl: string | null = null;

    const loadContent = async () => {
      setLoading(true);
      setError(null);
      try {
        // AList direct links often require authentication headers
        const headers: HeadersInit = {
          'Authorization': config.token,
          'AList-Token': config.token
        };

        const response = await fetch(url, { headers });
        if (!response.ok) {
          if (response.status === 403 || response.status === 401) {
            throw new Error('Access denied. Check server permissions.');
          }
          throw new Error(`Server returned ${response.status}. Try opening in browser.`);
        }

        if (type === 'text') {
          const text = await response.text();
          setTextContent(text);
        } else if (type === 'pdf') {
          const blob = await response.blob();
          const pdfBlob = new Blob([blob], { type: 'application/pdf' });
          currentBlobUrl = URL.createObjectURL(pdfBlob);
          setBlobUrl(currentBlobUrl);
        }
      } catch (err: any) {
        setError(err.message || 'An unexpected error occurred while previewing.');
      } finally {
        setLoading(false);
      }
    };

    if (type === 'text' || type === 'pdf') {
      loadContent();
    } else {
      // Images can load directly via src
      setLoading(false);
    }

    return () => {
      if (currentBlobUrl) {
        URL.revokeObjectURL(currentBlobUrl);
      }
    };
  }, [url, type, config.token]);

  return (
    <div className="fixed inset-0 z-[60] flex flex-col bg-white animate-fade-in">
      {/* Header */}
      <header className="flex flex-col border-b border-gray-100 bg-white sticky top-0 z-10 shadow-sm pt-safe">
        <div className="flex items-center justify-between px-4 py-3">
          <button onClick={onClose} className="p-2 hover:bg-gray-100 rounded-full transition-colors">
            <X className="w-6 h-6 text-gray-600" />
          </button>
          <div className="flex-1 min-w-0 px-4 text-center">
            <h2 className="text-sm font-semibold text-gray-800 truncate">{file.name}</h2>
          </div>
          <div className="flex items-center gap-1">
            <a 
              href={url} 
              download={file.name}
              className="p-2 hover:bg-indigo-50 text-indigo-600 rounded-full transition-colors"
            >
              <Download className="w-5 h-5" />
            </a>
          </div>
        </div>
      </header>

      {/* Content Area */}
      <main className="flex-1 overflow-auto bg-[#fafafa] relative flex flex-col">
        {loading ? (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-white/80 backdrop-blur-sm z-20">
            <RefreshCw className="w-10 h-10 text-indigo-600 animate-spin" />
            <p className="text-sm text-gray-500 font-medium tracking-wide uppercase">Buffering Content...</p>
          </div>
        ) : error ? (
          <div className="flex-1 flex flex-col items-center justify-center p-6 text-center">
            <div className="w-20 h-20 bg-red-50 rounded-3xl flex items-center justify-center mb-6">
              <AlertCircle className="w-10 h-10 text-red-500" />
            </div>
            <h3 className="text-xl font-bold text-gray-900 mb-2">Can't Load Preview</h3>
            <p className="text-sm text-gray-500 mb-8 max-w-xs mx-auto leading-relaxed">
              {error}
            </p>
            <div className="flex flex-col gap-3 w-full max-w-xs">
              <a 
                href={url}
                target="_blank"
                rel="noreferrer"
                className="flex items-center justify-center gap-2 px-6 py-4 bg-indigo-600 text-white rounded-2xl text-sm font-bold shadow-lg shadow-indigo-100 active:scale-[0.98] transition-all"
              >
                <Maximize2 className="w-4 h-4" />
                Open in System Browser
              </a>
              <button 
                onClick={onClose}
                className="px-6 py-4 bg-white border border-gray-200 text-gray-600 rounded-2xl text-sm font-bold active:bg-gray-50"
              >
                Dismiss
              </button>
            </div>
          </div>
        ) : type === 'image' ? (
          <div className="flex-1 w-full flex items-center justify-center p-4">
            <img 
              src={url} 
              alt={file.name} 
              className="max-w-full max-h-full object-contain shadow-2xl rounded-xl"
            />
          </div>
        ) : type === 'pdf' ? (
          <div className="flex-1 w-full flex flex-col relative">
            {blobUrl ? (
              <>
                {/* PDF Object/Embed Container */}
                <div className="flex-1 w-full h-full relative">
                  <object
                    data={blobUrl}
                    type="application/pdf"
                    className="w-full h-full border-none"
                  >
                    <div className="flex flex-col items-center justify-center h-full p-8 text-center">
                      <FileText className="w-16 h-16 text-gray-300 mb-4" />
                      <p className="text-gray-600 mb-6">Your browser doesn't support embedded PDF preview.</p>
                      <a 
                        href={blobUrl} 
                        target="_blank" 
                        rel="noreferrer"
                        className="px-8 py-3 bg-indigo-600 text-white rounded-xl font-bold"
                      >
                        Open PDF Separately
                      </a>
                    </div>
                  </object>
                </div>
                {/* Floating "External Open" Helper for Mobile */}
                <div className="absolute bottom-6 right-6 z-10">
                   <a 
                    href={blobUrl} 
                    target="_blank" 
                    rel="noreferrer"
                    className="flex items-center justify-center w-14 h-14 bg-indigo-600 text-white rounded-full shadow-2xl active:scale-90 transition-transform"
                    title="Open in Full Screen"
                   >
                     <Maximize2 className="w-6 h-6" />
                   </a>
                </div>
              </>
            ) : (
              <div className="flex-1 flex items-center justify-center">
                <p className="text-gray-400">PDF data is empty.</p>
              </div>
            )}
          </div>
        ) : (
          <div className="flex-1 p-4 md:p-8 max-w-4xl mx-auto w-full overflow-auto">
            <pre className="text-xs md:text-sm font-mono text-gray-700 whitespace-pre-wrap break-words bg-white p-6 rounded-3xl shadow-sm border border-gray-100 leading-relaxed overflow-x-auto">
              {textContent}
            </pre>
          </div>
        )}
      </main>

      <style>{`
        @keyframes fade-in {
          from { opacity: 0; transform: scale(0.98); }
          to { opacity: 1; transform: scale(1); }
        }
        .animate-fade-in {
          animation: fade-in 0.2s cubic-bezier(0.4, 0, 0.2, 1);
        }
      `}</style>
    </div>
  );
};

export default PreviewModal;
