
import React, { useState, useEffect } from 'react';
import { X, Download, RefreshCw, AlertCircle, ExternalLink } from 'lucide-react';
import { AListFile } from '../types';

interface Props {
  file: AListFile;
  url: string;
  type: 'image' | 'text' | 'pdf';
  onClose: () => void;
}

const PreviewModal: React.FC<Props> = ({ file, url, type, onClose }) => {
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
        const response = await fetch(url);
        if (!response.ok) throw new Error('Failed to load file content from server');

        if (type === 'text') {
          const text = await response.text();
          setTextContent(text);
        } else if (type === 'pdf') {
          // Fetch as blob to bypass Content-Disposition: attachment headers
          const blob = await response.blob();
          // Force MIME type if server returned something else
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

    // Cleanup local URL on unmount
    return () => {
      if (currentBlobUrl) {
        URL.revokeObjectURL(currentBlobUrl);
      }
    };
  }, [url, type]);

  return (
    <div className="fixed inset-0 z-[60] flex flex-col bg-white animate-fade-in">
      {/* Header */}
      <header className="flex items-center justify-between px-4 py-3 border-b border-gray-100 bg-white sticky top-0 z-10 shadow-sm">
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
            title="Download"
          >
            <Download className="w-5 h-5" />
          </a>
        </div>
      </header>

      {/* Content Area */}
      <main className="flex-1 overflow-auto bg-[#fafafa] relative">
        {loading ? (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-white/80 backdrop-blur-sm z-20">
            <RefreshCw className="w-10 h-10 text-indigo-600 animate-spin" />
            <p className="text-sm text-gray-500 font-medium">Preparing preview...</p>
          </div>
        ) : error ? (
          <div className="h-full flex flex-col items-center justify-center p-6 text-center">
            <div className="w-16 h-16 bg-red-50 rounded-full flex items-center justify-center mb-4">
              <AlertCircle className="w-8 h-8 text-red-500" />
            </div>
            <h3 className="text-lg font-bold text-gray-800">Preview Unavailable</h3>
            <p className="text-sm text-gray-400 mt-2 mb-8 max-w-xs mx-auto">
              {error}
            </p>
            <div className="flex flex-col gap-3 w-full max-w-xs">
              <a 
                href={url}
                target="_blank"
                rel="noreferrer"
                className="flex items-center justify-center gap-2 px-6 py-3 bg-indigo-600 text-white rounded-2xl text-sm font-semibold shadow-lg shadow-indigo-100 active:scale-[0.98] transition-transform"
              >
                <ExternalLink className="w-4 h-4" />
                Open in Browser
              </a>
              <button 
                onClick={onClose}
                className="px-6 py-3 bg-white border border-gray-200 text-gray-600 rounded-2xl text-sm font-semibold"
              >
                Go Back
              </button>
            </div>
          </div>
        ) : type === 'image' ? (
          <div className="h-full w-full flex items-center justify-center p-4">
            <img 
              src={url} 
              alt={file.name} 
              className="max-w-full max-h-full object-contain shadow-md rounded-lg"
              onLoad={() => setLoading(false)}
            />
          </div>
        ) : type === 'pdf' ? (
          <div className="h-full w-full flex flex-col">
            {blobUrl ? (
              <iframe 
                src={`${blobUrl}#toolbar=0&navpanes=0&scrollbar=1`} 
                className="w-full h-full border-none"
                title={file.name}
              />
            ) : (
              <div className="h-full flex items-center justify-center">
                <p className="text-gray-400">Unable to generate local PDF link.</p>
              </div>
            )}
          </div>
        ) : (
          <div className="p-4 md:p-8 max-w-4xl mx-auto w-full">
            <pre className="text-xs md:text-sm font-mono text-gray-700 whitespace-pre-wrap break-words bg-white p-6 rounded-2xl shadow-sm border border-gray-100 leading-relaxed overflow-x-auto">
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
