
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { ServerConfig, AListFile } from '../types';
import { AListService } from '../services/alistService';
import FileIcon from './FileIcon';
import ActionSheet from './ActionSheet';
import { ChevronRight, Home, LayoutGrid, List, Search, ArrowLeft, MoreVertical, RefreshCw, Plus, Upload, AlertCircle } from 'lucide-react';

interface Props {
  config: ServerConfig;
  onSessionExpired: () => void;
}

const FileBrowser: React.FC<Props> = ({ config, onSessionExpired }) => {
  const [path, setPath] = useState('/');
  const [files, setFiles] = useState<AListFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<'list' | 'grid'>('list');
  const [selectedFile, setSelectedFile] = useState<{ file: AListFile, path: string } | null>(null);
  
  // Touch gesture refs
  const touchStartPos = useRef({ x: 0, y: 0 });
  const touchStartTime = useRef(0);
  
  const serviceRef = useRef(new AListService(config));
  const fileInputRef = useRef<HTMLInputElement>(null);

  const fetchFiles = useCallback(async (currentPath: string, forceRefresh = false) => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const response = await serviceRef.current.listFiles(currentPath, 1, 100, forceRefresh);
      const sorted = (response.data.content || []).sort((a, b) => {
        if (a.is_dir && !b.is_dir) return -1;
        if (!a.is_dir && b.is_dir) return 1;
        return a.name.localeCompare(b.name);
      });
      setFiles(sorted);
    } catch (err: any) {
      if (err.message.includes('Unauthorized')) {
        onSessionExpired();
      }
      setErrorMsg(`Failed to load files: ${err.message}`);
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [onSessionExpired]);

  useEffect(() => {
    fetchFiles(path);
  }, [path, fetchFiles]);

  const handleNavigate = (newPath: string) => {
    setPath(newPath);
    setSearchQuery('');
  };

  const goBack = useCallback(() => {
    if (path === '/') return;
    const parts = path.split('/').filter(Boolean);
    parts.pop();
    setPath('/' + parts.join('/'));
  }, [path]);

  // Gesture handling
  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartPos.current = { 
      x: e.touches[0].clientX, 
      y: e.touches[0].clientY 
    };
    touchStartTime.current = Date.now();
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    const deltaX = e.changedTouches[0].clientX - touchStartPos.current.x;
    const deltaY = e.changedTouches[0].clientY - touchStartPos.current.y;
    const duration = Date.now() - touchStartTime.current;

    // Thresholds for a valid "back" swipe
    // 1. Swipe must be from left to right (deltaX > 0)
    // 2. Minimum distance: 80px
    // 3. Maximum vertical deviation: 60px (to distinguish from scrolling)
    // 4. Maximum duration: 400ms (to distinguish from slow dragging)
    // 5. Start position: Avoid very edge (0-20px) if we want to let system handle it, 
    //    BUT user asked to avoid exiting, so we handle it if it starts slightly inward or if we want to "take over".
    //    Let's check if start X was between 0 and 100px from left edge.
    const isHorizontal = Math.abs(deltaX) > Math.abs(deltaY) * 1.5;
    const startedNearEdge = touchStartPos.current.x < 120; 

    if (startedNearEdge && deltaX > 80 && Math.abs(deltaY) < 60 && duration < 400 && isHorizontal) {
      goBack();
    }
  };

  const handleUploadClick = () => {
    setErrorMsg(null);
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    try {
      await serviceRef.current.uploadFile(path, file);
      await fetchFiles(path, true);
    } catch (err: any) {
      setErrorMsg(`Upload failed: ${err.message}. Ensure the storage is writable.`);
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const formatDate = (dateStr: string) => {
    const d = new Date(dateStr);
    return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const filteredFiles = files.filter(f => f.name.toLowerCase().includes(searchQuery.toLowerCase()));
  const breadcrumbs = path.split('/').filter(Boolean);

  return (
    <div 
      className="h-full flex flex-col bg-[#f7f2fa] relative overflow-hidden"
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      {/* Search & View Toggle */}
      <div className="px-4 py-3 flex gap-2 items-center bg-white border-b border-gray-100 z-10">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input 
            type="text" 
            placeholder="Search files..."
            className="w-full pl-9 pr-4 py-2 bg-gray-100 border-none rounded-xl text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
        <button 
          onClick={() => setViewMode(v => v === 'list' ? 'grid' : 'list')}
          className="p-2 bg-gray-100 rounded-xl text-gray-600 hover:bg-gray-200"
        >
          {viewMode === 'list' ? <LayoutGrid className="w-5 h-5" /> : <List className="w-5 h-5" />}
        </button>
      </div>

      {/* Breadcrumbs */}
      <div className="flex items-center gap-1 px-4 py-2 overflow-x-auto whitespace-nowrap hide-scrollbar bg-white/50 backdrop-blur-sm border-b border-gray-100 text-sm z-10">
        <button 
          onClick={() => handleNavigate('/')}
          className="p-1 hover:text-indigo-600 flex items-center text-gray-500"
        >
          <Home className="w-4 h-4" />
        </button>
        {breadcrumbs.map((crumb, idx) => (
          <React.Fragment key={idx}>
            <ChevronRight className="w-4 h-4 text-gray-300 shrink-0" />
            <button 
              onClick={() => handleNavigate('/' + breadcrumbs.slice(0, idx + 1).join('/'))}
              className={`hover:text-indigo-600 ${idx === breadcrumbs.length - 1 ? 'font-semibold text-gray-800' : 'text-gray-500'}`}
            >
              {crumb}
            </button>
          </React.Fragment>
        ))}
      </div>

      {/* Error Banner */}
      {errorMsg && (
        <div className="mx-4 mt-2 p-3 bg-red-50 text-red-700 text-xs rounded-xl border border-red-100 flex items-center gap-2 shadow-sm animate-pulse z-10">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <p>{errorMsg}</p>
          <button onClick={() => setErrorMsg(null)} className="ml-auto font-bold opacity-50 hover:opacity-100">✕</button>
        </div>
      )}

      {/* File List */}
      <div className="flex-1 overflow-y-auto px-4 pt-2 pb-24 touch-pan-y">
        {loading ? (
          <div className="h-40 flex flex-col items-center justify-center gap-2">
            <RefreshCw className="w-6 h-6 text-indigo-600 animate-spin" />
            <span className="text-sm text-gray-500">Scanning storage...</span>
          </div>
        ) : filteredFiles.length === 0 ? (
          <div className="h-40 flex flex-col items-center justify-center text-gray-400">
            <p className="text-sm">Empty folder</p>
          </div>
        ) : (
          <div className={viewMode === 'list' ? 'flex flex-col gap-1' : 'grid grid-cols-3 sm:grid-cols-4 gap-4'}>
            {filteredFiles.map((file) => (
              <div 
                key={file.name}
                onClick={() => file.is_dir ? handleNavigate((path === '/' ? '' : path) + '/' + file.name) : setSelectedFile({ file, path: (path === '/' ? '' : path) + '/' + file.name })}
                className={`
                  relative group transition-all active:scale-[0.97]
                  ${viewMode === 'list' 
                    ? 'flex items-center gap-4 p-3 bg-white rounded-2xl border border-transparent active:border-indigo-100 hover:bg-indigo-50/30' 
                    : 'flex flex-col items-center p-3 bg-white rounded-2xl border border-transparent shadow-sm'
                  }
                `}
              >
                <div className={`${viewMode === 'list' ? 'shrink-0' : 'w-full aspect-square flex items-center justify-center mb-2'}`}>
                  {file.thumb ? (
                     <img src={file.thumb} alt="" className="w-10 h-10 rounded-lg object-cover" />
                  ) : (
                    <FileIcon isDir={file.is_dir} name={file.name} className={viewMode === 'list' ? 'w-8 h-8' : 'w-12 h-12'} />
                  )}
                </div>
                
                <div className={`flex-1 min-w-0 ${viewMode === 'grid' ? 'text-center' : ''}`}>
                  <h3 className={`font-medium text-gray-800 truncate ${viewMode === 'list' ? 'text-sm' : 'text-xs w-full px-1'}`}>
                    {file.name}
                  </h3>
                  {viewMode === 'list' && (
                    <p className="text-[10px] text-gray-400 mt-0.5 flex items-center gap-2">
                      {!file.is_dir && <span>{formatSize(file.size)}</span>}
                      <span>{formatDate(file.modified)}</span>
                    </p>
                  )}
                </div>

                {!file.is_dir && viewMode === 'list' && (
                   <button 
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelectedFile({ file, path: (path === '/' ? '' : path) + '/' + file.name });
                    }}
                    className="p-2 hover:bg-gray-100 rounded-full text-gray-400"
                   >
                     <MoreVertical className="w-5 h-5" />
                   </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* FAB Container */}
      <div className="fixed bottom-6 right-6 flex flex-col gap-4 z-30">
        <button 
          onClick={handleUploadClick}
          disabled={uploading}
          className="w-14 h-14 bg-indigo-600 text-white rounded-2xl shadow-xl flex items-center justify-center active:scale-95 transition-all disabled:opacity-50"
        >
          {uploading ? <RefreshCw className="w-6 h-6 animate-spin" /> : <Plus className="w-8 h-8" />}
        </button>
        <input type="file" ref={fileInputRef} className="hidden" onChange={handleFileChange} />
      </div>

      {/* Back Button */}
      {path !== '/' && (
        <button 
          onClick={goBack}
          className="fixed bottom-6 left-6 w-12 h-12 bg-white text-indigo-600 rounded-full shadow-lg border border-indigo-50 flex items-center justify-center active:scale-90 transition-all z-30"
        >
          <ArrowLeft className="w-6 h-6" />
        </button>
      )}

      {/* Status Overlay */}
      {uploading && (
        <div className="fixed bottom-24 right-6 bg-indigo-600 text-white px-4 py-2 rounded-xl shadow-lg flex items-center gap-3 z-40">
          <Upload className="w-4 h-4 animate-bounce" />
          <span className="text-xs font-semibold">Uploading...</span>
        </div>
      )}

      {selectedFile && (
        <ActionSheet 
          file={selectedFile.file} 
          path={selectedFile.path}
          config={config}
          onClose={() => setSelectedFile(null)}
          onRefresh={() => fetchFiles(path, true)}
        />
      )}
    </div>
  );
};

export default FileBrowser;
