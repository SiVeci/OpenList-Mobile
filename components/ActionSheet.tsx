
import React, { useState, useEffect } from 'react';
import { AListFile, ServerConfig } from '../types';
import { AListService } from '../services/alistService';
import { 
  Download, 
  Trash2, 
  Play, 
  Link as LinkIcon, 
  X,
  Eye,
  ExternalLink as ExtIcon,
  Maximize,
  RefreshCw,
  AlertTriangle
} from 'lucide-react';
import FileIcon from './FileIcon';
import PreviewModal from './PreviewModal';

interface Props {
  file: AListFile;
  path: string;
  config: ServerConfig;
  onClose: () => void;
  onRefresh: () => void;
}

const ActionSheet: React.FC<Props> = ({ file, path, config, onClose, onRefresh }) => {
  const [rawUrl, setRawUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showPreview, setShowPreview] = useState(false);

  useEffect(() => {
    const fetchLink = async () => {
      try {
        const service = new AListService(config);
        const detail = await service.getFileDetail(path);
        setRawUrl(detail.data.raw_url);
      } catch (err: any) {
        setError("Failed to fetch direct link: " + err.message);
      } finally {
        setLoading(false);
      }
    };
    fetchLink();
  }, [path, config]);

  const handleCopyLink = () => {
    if (rawUrl) {
      navigator.clipboard.writeText(rawUrl);
      alert('Link copied to clipboard');
    }
  };

  const handleExternalPlayer = (player: 'vlc' | 'mx' | 'nplayer') => {
    if (!rawUrl) return;
    let url = '';
    
    switch(player) {
      case 'vlc': url = `vlc://${rawUrl}`; break;
      case 'mx': url = `intent:${rawUrl}#Intent;package=com.mxtech.videoplayer.ad;S.title=${encodeURIComponent(file.name)};end`; break;
      case 'nplayer': url = `nplayer-${rawUrl}`; break;
    }
    window.location.href = url;
  };

  const executeDelete = async () => {
    if (deleting) return;
    setDeleting(true);
    setError(null);
    try {
      const service = new AListService(config);
      await service.deleteFile(path);
      onRefresh();
      onClose();
    } catch (err: any) {
      setError("Delete failed: " + err.message);
      setDeleting(false);
      setConfirmDelete(false);
    }
  };

  const ext = file.name.split('.').pop()?.toLowerCase() || '';
  const isImage = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext);
  const isPdf = ext === 'pdf';
  const isText = ['txt', 'md', 'json', 'yaml', 'yml', 'js', 'ts', 'py', 'css', 'html', 'conf', 'ini', 'log'].includes(ext);
  const isVideo = ['mp4', 'mkv', 'avi', 'mov', 'flv', 'wmv', 'rmvb'].includes(ext);

  const canPreview = isImage || isText || isPdf;

  return (
    <>
      <div className="fixed inset-0 z-50 flex items-end justify-center">
        {/* Backdrop */}
        <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
        
        {/* Sheet Content */}
        <div className="relative w-full max-w-lg bg-white rounded-t-3xl shadow-2xl p-6 animate-slide-up pb-10">
          <div className="w-12 h-1.5 bg-gray-200 rounded-full mx-auto mb-6" />
          
          <div className="flex items-start gap-4 mb-8">
            <div className="shrink-0 bg-gray-50 p-4 rounded-2xl">
              <FileIcon isDir={false} name={file.name} className="w-10 h-10" />
            </div>
            <div className="flex-1 min-w-0">
              <h3 className="font-bold text-gray-900 truncate pr-8">{file.name}</h3>
              <p className="text-xs text-gray-400 mt-1 uppercase tracking-widest font-semibold">
                {ext} File
              </p>
            </div>
            <button onClick={onClose} className="p-2 hover:bg-gray-100 rounded-full text-gray-400">
              <X className="w-6 h-6" />
            </button>
          </div>

          {loading ? (
            <div className="py-10 flex justify-center">
              <div className="w-8 h-8 border-4 border-indigo-600 border-t-transparent rounded-full animate-spin" />
            </div>
          ) : confirmDelete ? (
            <div className="bg-red-50 p-6 rounded-2xl border border-red-100 animate-in fade-in zoom-in duration-200">
              <div className="flex items-center gap-3 text-red-700 mb-4">
                <AlertTriangle className="w-6 h-6" />
                <span className="font-bold">Confirm Deletion?</span>
              </div>
              <p className="text-sm text-red-600 mb-6 leading-relaxed">
                This action cannot be undone. Are you sure you want to permanently delete <strong>{file.name}</strong>?
              </p>
              <div className="grid grid-cols-2 gap-3">
                <button 
                  onClick={() => setConfirmDelete(false)}
                  disabled={deleting}
                  className="py-3 bg-white text-gray-600 rounded-xl font-semibold border border-gray-200 active:scale-95 transition-transform"
                >
                  Cancel
                </button>
                <button 
                  onClick={executeDelete}
                  disabled={deleting}
                  className="py-3 bg-red-600 text-white rounded-xl font-semibold shadow-lg shadow-red-100 flex items-center justify-center gap-2 active:scale-95 transition-transform"
                >
                  {deleting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
                  Confirm
                </button>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-4">
              {/* Actions */}
              {canPreview ? (
                <button 
                  onClick={() => setShowPreview(true)}
                  className="flex flex-col items-center justify-center p-4 bg-indigo-600 text-white rounded-2xl gap-2 hover:bg-indigo-700 transition-colors col-span-2 shadow-lg shadow-indigo-100"
                >
                  <Eye className="w-6 h-6" />
                  <span className="text-sm font-semibold">Preview Now</span>
                </button>
              ) : isVideo ? (
                <button 
                  onClick={() => handleExternalPlayer('vlc')}
                  className="flex flex-col items-center justify-center p-4 bg-indigo-600 text-white rounded-2xl gap-2 hover:bg-indigo-700 transition-colors col-span-2 shadow-lg shadow-indigo-100"
                >
                  <Play className="w-6 h-6" />
                  <span className="text-sm font-semibold">Play in VLC</span>
                </button>
              ) : null}

              <a 
                href={rawUrl || '#'} 
                download={file.name}
                target="_blank"
                rel="noreferrer"
                className={`flex flex-col items-center justify-center p-4 bg-gray-50 text-gray-700 rounded-2xl gap-2 hover:bg-gray-100 transition-colors ${!(canPreview || isVideo) ? 'col-span-2' : ''}`}
              >
                <Download className="w-6 h-6" />
                <span className="text-sm font-semibold">Download</span>
              </a>

              <button 
                onClick={handleCopyLink}
                className="flex flex-col items-center justify-center p-4 bg-gray-50 text-gray-700 rounded-2xl gap-2 hover:bg-gray-100 transition-colors"
              >
                <LinkIcon className="w-6 h-6" />
                <span className="text-sm font-semibold">Copy Link</span>
              </button>

              {isVideo && (
                <>
                  <button 
                    onClick={() => handleExternalPlayer('mx')}
                    className="flex flex-col items-center justify-center p-4 bg-blue-50 text-blue-700 rounded-2xl gap-2 hover:bg-blue-100 transition-colors"
                  >
                    <ExtIcon className="w-6 h-6" />
                    <span className="text-sm font-semibold">MX Player</span>
                  </button>
                  <button 
                    onClick={() => handleExternalPlayer('nplayer')}
                    className="flex flex-col items-center justify-center p-4 bg-teal-50 text-teal-700 rounded-2xl gap-2 hover:bg-teal-100 transition-colors"
                  >
                    <Maximize className="w-6 h-6" />
                    <span className="text-sm font-semibold">NPlayer</span>
                  </button>
                </>
              )}

              <button 
                onClick={() => setConfirmDelete(true)}
                className="flex flex-col items-center justify-center p-4 bg-red-50 text-red-700 rounded-2xl gap-2 hover:bg-red-100 transition-colors col-span-2"
              >
                <Trash2 className="w-6 h-6" />
                <span className="text-sm font-semibold">Delete File</span>
              </button>
            </div>
          )}

          {error && (
            <div className="mt-4 p-4 bg-red-50 text-red-600 text-xs rounded-xl border border-red-100 text-center font-medium">
              {error}
            </div>
          )}
        </div>

        <style>{`
          @keyframes slide-up {
            from { transform: translateY(100%); }
            to { transform: translateY(0); }
          }
          .animate-slide-up {
            animation: slide-up 0.3s cubic-bezier(0.4, 0, 0.2, 1);
          }
        `}</style>
      </div>

      {showPreview && rawUrl && (
        <PreviewModal 
          file={file} 
          url={rawUrl} 
          type={isImage ? 'image' : isPdf ? 'pdf' : 'text'} 
          onClose={() => setShowPreview(false)} 
        />
      )}
    </>
  );
};

export default ActionSheet;
