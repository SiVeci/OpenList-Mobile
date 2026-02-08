
import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { ServerConfig, AListFile } from '../types';
import { AListService } from '../services/alistService';
import FileIcon from './FileIcon';
import ActionSheet from './ActionSheet';
import { 
  ChevronRight, 
  Home, 
  LayoutGrid, 
  List, 
  Search, 
  ArrowLeft, 
  MoreVertical, 
  RefreshCw, 
  Plus, 
  Upload, 
  AlertCircle,
  ArrowUpDown,
  Check,
  ArrowUpNarrowWide,
  ArrowDownWideNarrow,
  FolderTree,
  Filter,
  FileVideo,
  FileImage,
  FileText,
  FileBox,
  Hash,
  X,
  Trash2,
  Copy,
  CheckSquare,
  Square,
  Download,
  Info
} from 'lucide-react';

interface Props {
  config: ServerConfig;
  onSessionExpired: () => void;
}

type SortKey = 'name' | 'modified' | 'size';
type SortOrder = 'asc' | 'desc';
type FilterType = 'all' | 'video' | 'image' | 'doc' | 'others' | 'custom';

const FileBrowser: React.FC<Props> = ({ config, onSessionExpired }) => {
  const [path, setPath] = useState('/');
  const [files, setFiles] = useState<AListFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<'list' | 'grid'>('list');
  const [selectedFile, setSelectedFile] = useState<{ file: AListFile, path: string } | null>(null);
  const [isSortMenuOpen, setIsSortMenuOpen] = useState(false);
  const [isFilterMenuOpen, setIsFilterMenuOpen] = useState(false);
  
  // Pull-to-refresh state
  const [pullDistance, setPullDistance] = useState(0);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  
  // Constants for high-quality mechanical PTR feel
  const PULL_LIMIT = 110; // The hard "wall"
  const PULL_TRIGGER_ZONE = 100; // Point where refresh is armed
  const DAMPING_COEFFICIENT = 0.65; // Lower = more resistance
  
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  // Selection State
  const [selectedItemNames, setSelectedItemNames] = useState<Set<string>>(new Set());
  const [isSelectionMode, setIsSelectionMode] = useState(false);
  const [batchActionLoading, setBatchActionLoading] = useState(false);
  const [showBatchDeleteConfirm, setShowBatchDeleteConfirm] = useState(false);

  // Filter State
  const [filterType, setFilterType] = useState<FilterType>('all');
  const [customExt, setCustomExt] = useState('');

  // Sorting Preferences (Persisted)
  const [sortKey, setSortKey] = useState<SortKey>(() => (localStorage.getItem('alist_sort_key') as SortKey) || 'name');
  const [sortOrder, setSortOrder] = useState<SortOrder>(() => (localStorage.getItem('alist_sort_order') as SortOrder) || 'asc');
  const [foldersFirst, setFoldersFirst] = useState<boolean>(() => localStorage.getItem('alist_folders_first') !== 'false');

  // Gesture refs
  const touchStartPos = useRef({ x: 0, y: 0 });
  const touchStartTime = useRef(0);
  const longPressTimer = useRef<number | null>(null);
  const wasLongPress = useRef(false);
  const isPulling = useRef(false);
  
  const serviceRef = useRef(new AListService(config));
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Toast Timer
  useEffect(() => {
    if (toastMsg) {
      const timer = setTimeout(() => setToastMsg(null), 2000);
      return () => clearTimeout(timer);
    }
  }, [toastMsg]);

  // Persistence Effects
  useEffect(() => {
    localStorage.setItem('alist_sort_key', sortKey);
    localStorage.setItem('alist_sort_order', sortOrder);
    localStorage.setItem('alist_folders_first', String(foldersFirst));
  }, [sortKey, sortOrder, foldersFirst]);

  const fetchFiles = useCallback(async (currentPath: string, forceRefresh = false) => {
    setLoading(true);
    setErrorMsg(null);
    try {
      const response = await serviceRef.current.listFiles(currentPath, 1, 100, forceRefresh);
      setFiles(response.data.content || []);
      if (forceRefresh) {
        setToastMsg("Sync Complete");
      }
    } catch (err: any) {
      if (err.message.includes('Unauthorized')) {
        onSessionExpired();
      }
      setErrorMsg(`Sync failed: ${err.message}`);
    } finally {
      setLoading(false);
      setIsRefreshing(false);
      setPullDistance(0);
    }
  }, [onSessionExpired]);

  useEffect(() => {
    fetchFiles(path);
    setIsSelectionMode(false);
    setSelectedItemNames(new Set());
  }, [path, fetchFiles]);

  // Client-side Sorting and Filtering Logic
  const sortedAndFilteredFiles = useMemo(() => {
    const videoExts = ['mp4', 'mkv', 'avi', 'mov', 'flv', 'wmv', 'rmvb'];
    const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'];
    const docExts = [
      'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 
      'txt', 'md', 'json', 'yaml', 'yml', 'js', 'ts', 'py', 'css', 'html', 'conf', 'ini', 'log', 
      'tsx', 'jsx', 'sh', 'sql', 'xml', 'csv', 'go', 'c', 'cpp', 'java' 
    ];

    let result = files.filter(f => {
      const matchesSearch = f.name.toLowerCase().includes(searchQuery.toLowerCase());
      if (!matchesSearch) return false;

      if (f.is_dir) return true;

      const ext = f.name.split('.').pop()?.toLowerCase() || '';

      switch (filterType) {
        case 'video': return videoExts.includes(ext);
        case 'image': return imageExts.includes(ext);
        case 'doc': return docExts.includes(ext);
        case 'others': 
          return !videoExts.includes(ext) && !imageExts.includes(ext) && !docExts.includes(ext);
        case 'custom':
          return customExt ? ext === customExt.toLowerCase().trim().replace('.', '') : true;
        default: return true;
      }
    });
    
    result.sort((a, b) => {
      if (foldersFirst) {
        if (a.is_dir && !b.is_dir) return -1;
        if (!a.is_dir && b.is_dir) return 1;
      }

      let comparison = 0;
      switch (sortKey) {
        case 'name':
          comparison = a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' });
          break;
        case 'size':
          comparison = (a.size || 0) - (b.size || 0);
          break;
        case 'modified':
          comparison = new Date(a.modified).getTime() - new Date(b.modified).getTime();
          break;
      }

      return sortOrder === 'asc' ? comparison : -comparison;
    });

    return result;
  }, [files, searchQuery, sortKey, sortOrder, foldersFirst, filterType, customExt]);

  const handleNavigate = (newPath: string) => {
    setPath(newPath);
    setSearchQuery('');
    setFilterType('all');
  };

  const goBack = useCallback(() => {
    if (path === '/') return;
    const parts = path.split('/').filter(Boolean);
    parts.pop();
    setPath('/' + parts.join('/'));
    setFilterType('all');
  }, [path]);

  // Gesture Handlers
  const handlePointerDown = (e: React.PointerEvent, file: AListFile) => {
    wasLongPress.current = false;
    touchStartPos.current = { x: e.clientX, y: e.clientY };
    touchStartTime.current = Date.now();

    longPressTimer.current = window.setTimeout(() => {
      if (!isSelectionMode) {
        setIsSelectionMode(true);
        setSelectedItemNames(new Set([file.name]));
        wasLongPress.current = true;
        if ('vibrate' in navigator) navigator.vibrate(50);
      }
    }, 800);
  };

  const handlePointerUp = () => {
    if (longPressTimer.current) {
      clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    const deltaX = Math.abs(e.clientX - touchStartPos.current.x);
    const deltaY = Math.abs(e.clientY - touchStartPos.current.y);
    if (deltaX > 10 || deltaY > 10) {
      if (longPressTimer.current) {
        clearTimeout(longPressTimer.current);
        longPressTimer.current = null;
      }
    }
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length !== 1) return;
    touchStartPos.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
    touchStartTime.current = Date.now();
    // Only arm PTR if at top of scroll
    isPulling.current = scrollContainerRef.current?.scrollTop === 0;
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    if (e.touches.length !== 1 || !isPulling.current || isRefreshing || isSelectionMode) return;
    
    const currentY = e.touches[0].clientY;
    const currentX = e.touches[0].clientX;
    const deltaY = currentY - touchStartPos.current.y;
    const deltaX = Math.abs(currentX - touchStartPos.current.x);

    // Only handle pull if it's primarily vertical and downwards
    if (deltaY > 0 && deltaY > deltaX) {
      // Enhanced damping logic using a power function for a "heavy" feel
      // This creates exponential resistance as the pull distance increases
      let rawDistance = Math.pow(deltaY, 0.8) * DAMPING_COEFFICIENT * 2;
      
      // Strict mechanical limit at PULL_LIMIT
      const finalDistance = Math.min(rawDistance, PULL_LIMIT);
      
      setPullDistance(finalDistance);
      
      // Feedback at the trigger point
      if (finalDistance >= PULL_TRIGGER_ZONE && pullDistance < PULL_TRIGGER_ZONE) {
         if ('vibrate' in navigator) navigator.vibrate(12);
      }

      // Block native scrolling and browser reload
      if (e.cancelable) e.preventDefault();
    } else {
      setPullDistance(0);
    }
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (e.changedTouches.length !== 1) return;
    const deltaX = e.changedTouches[0].clientX - touchStartPos.current.x;
    const deltaY = e.changedTouches[0].clientY - touchStartPos.current.y;
    const duration = Date.now() - touchStartTime.current;

    // Detect edge swipe back
    if (!isSelectionMode && touchStartPos.current.x < 40 && deltaX > 100 && Math.abs(deltaX) > Math.abs(deltaY) * 2 && duration < 500) {
      goBack();
      setPullDistance(0);
      return;
    }

    // Must reach the trigger zone (the hard stop area) to refresh
    if (pullDistance >= PULL_TRIGGER_ZONE && !isRefreshing) {
      setIsRefreshing(true);
      setPullDistance(50); // Snap back to holding position
      setToastMsg("Refreshing list...");
      fetchFiles(path, true);
    } else {
      setPullDistance(0);
    }
    isPulling.current = false;
  };

  const handleItemClick = (file: AListFile) => {
    if (wasLongPress.current) {
      wasLongPress.current = false;
      return;
    }
    if (isSelectionMode) {
      setSelectedItemNames(prev => {
        const next = new Set(prev);
        if (next.has(file.name)) {
          next.delete(file.name);
          if (next.size === 0) setIsSelectionMode(false);
        } else {
          next.add(file.name);
        }
        return next;
      });
      return;
    }
    if (file.is_dir) {
      handleNavigate((path === '/' ? '' : path) + '/' + file.name);
    } else {
      setSelectedFile({ file, path: (path === '/' ? '' : path) + '/' + file.name });
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

  const breadcrumbs = path.split('/').filter(Boolean);

  return (
    <div 
      className="h-full flex flex-col bg-[#f7f2fa] relative overflow-hidden"
      style={{ touchAction: 'pan-y' }}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
    >
      {/* Pull-to-refresh Indicator */}
      <div 
        className="absolute left-0 right-0 flex justify-center z-50 pointer-events-none"
        style={{ 
          top: 0, 
          transform: `translateY(${Math.max(pullDistance - 55, -55)}px)`,
          opacity: pullDistance > 10 ? 1 : 0,
          transition: isRefreshing ? 'transform 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275)' : 'none'
        }}
      >
        <div className={`
          flex items-center gap-2 bg-white rounded-full px-5 py-2.5 shadow-2xl border transition-all duration-300
          ${pullDistance >= PULL_TRIGGER_ZONE ? 'border-indigo-500 scale-105 shadow-indigo-100' : 'border-gray-100 scale-95'}
        `}>
          <RefreshCw 
            className={`w-5 h-5 transition-colors ${isRefreshing ? 'animate-spin text-indigo-600' : pullDistance >= PULL_TRIGGER_ZONE ? 'text-indigo-600' : 'text-gray-300'}`} 
            style={{ 
              transform: isRefreshing ? undefined : `rotate(${pullDistance * 4}deg)`,
              transition: isRefreshing ? undefined : 'none'
            }} 
          />
          {pullDistance >= PULL_TRIGGER_ZONE && !isRefreshing && (
            <span className="text-[10px] font-black text-indigo-600 uppercase tracking-widest animate-in fade-in slide-in-from-right-2 duration-300">Sync Data</span>
          )}
        </div>
      </div>

      {/* Toolbar Area */}
      <div className="bg-white border-b border-gray-100 z-10 shadow-sm shrink-0">
        <div className="px-4 py-3 flex gap-2 items-center">
          {isSelectionMode && (
            <button 
              onClick={() => {
                if (selectedItemNames.size === sortedAndFilteredFiles.length) {
                  setSelectedItemNames(new Set());
                  setIsSelectionMode(false);
                } else {
                  setSelectedItemNames(new Set(sortedAndFilteredFiles.map(f => f.name)));
                }
              }}
              className="p-2.5 rounded-2xl bg-indigo-600 text-white shrink-0 animate-in slide-in-from-left-2 duration-200 shadow-lg shadow-indigo-100"
            >
              <CheckSquare className="w-5 h-5" />
            </button>
          )}

          <div className="relative flex-1 min-w-0">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input 
              type="text" 
              placeholder="Search files..."
              className="w-full pl-9 pr-4 py-2.5 bg-gray-100 border-none rounded-2xl text-sm focus:ring-2 focus:ring-indigo-500 outline-none font-medium transition-all"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          <div className="flex gap-1 shrink-0">
            <button 
              onClick={() => setIsFilterMenuOpen(true)}
              className={`p-2.5 rounded-2xl border ${filterType !== 'all' ? 'bg-indigo-50 border-indigo-200 text-indigo-600' : 'bg-gray-100 border-transparent text-gray-600'}`}
            >
              <Filter className="w-5 h-5" />
            </button>
            <button 
              onClick={() => setIsSortMenuOpen(true)}
              className="p-2.5 bg-gray-100 border-transparent rounded-2xl text-gray-600"
            >
              <ArrowUpDown className="w-5 h-5" />
            </button>
            <button 
              onClick={() => setViewMode(v => v === 'list' ? 'grid' : 'list')}
              className="p-2.5 bg-gray-100 border-transparent rounded-2xl text-gray-600"
            >
              {viewMode === 'list' ? <LayoutGrid className="w-5 h-5" /> : <List className="w-5 h-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Breadcrumbs */}
      <div className="flex items-center gap-1 px-4 py-3 overflow-x-auto whitespace-nowrap hide-scrollbar bg-white/50 backdrop-blur-sm border-b border-gray-100 text-sm z-10 shrink-0">
        <button onClick={() => handleNavigate('/')} className="p-1 text-gray-400"><Home className="w-4 h-4" /></button>
        {breadcrumbs.map((crumb, idx) => (
          <React.Fragment key={idx}>
            <ChevronRight className="w-4 h-4 text-gray-300 shrink-0" />
            <button 
              onClick={() => handleNavigate('/' + breadcrumbs.slice(0, idx + 1).join('/'))}
              className={`px-1.5 py-0.5 rounded-lg transition-colors ${idx === breadcrumbs.length - 1 ? 'font-bold text-indigo-600 bg-indigo-50' : 'text-gray-500'}`}
            >
              {crumb}
            </button>
          </React.Fragment>
        ))}
      </div>

      {/* Main File List Container */}
      <div 
        ref={scrollContainerRef}
        className="flex-1 overflow-y-auto px-4 pt-2 pb-32 transition-transform ease-out"
        style={{ 
          transform: pullDistance > 0 ? `translateY(${pullDistance * 0.85}px)` : undefined,
          transitionDuration: isRefreshing ? '400ms' : pullDistance > 0 ? '0ms' : '300ms'
        }}
      >
        {loading && !isRefreshing ? (
          <div className="h-60 flex flex-col items-center justify-center gap-3">
            <div className="w-10 h-10 border-4 border-indigo-100 border-t-indigo-600 rounded-full animate-spin" />
            <span className="text-sm text-gray-400 font-bold uppercase tracking-widest">Scanning...</span>
          </div>
        ) : sortedAndFilteredFiles.length === 0 ? (
          <div className="h-60 flex flex-col items-center justify-center text-gray-300">
             <Search className="w-16 h-16 mb-4 opacity-30" />
             <p className="text-sm font-black uppercase tracking-widest">No matching files</p>
          </div>
        ) : (
          <div className={viewMode === 'list' ? 'flex flex-col gap-1.5' : 'grid grid-cols-3 sm:grid-cols-4 gap-4'}>
            {sortedAndFilteredFiles.map((file) => (
              <div 
                key={file.name}
                onPointerDown={(e) => handlePointerDown(e, file)}
                onPointerUp={handlePointerUp}
                onPointerLeave={handlePointerUp}
                onPointerMove={handlePointerMove}
                onClick={() => handleItemClick(file)}
                className={`
                  relative group transition-all active:scale-[0.96] overflow-hidden select-none
                  ${viewMode === 'list' 
                    ? 'flex items-center gap-4 p-3.5 rounded-[1.5rem] border transition-colors shadow-sm' 
                    : 'flex flex-col items-center p-3.5 rounded-[1.5rem] border transition-colors shadow-sm'
                  }
                  ${selectedItemNames.has(file.name) 
                    ? 'bg-indigo-50 border-indigo-200' 
                    : 'bg-white border-transparent hover:border-indigo-50'
                  }
                `}
                style={{ touchAction: 'pan-y' }}
              >
                {isSelectionMode && (
                  <div className={`absolute top-2.5 left-2.5 z-10 transition-transform ${selectedItemNames.has(file.name) ? 'scale-100' : 'scale-0'}`}>
                    <div className="bg-indigo-600 rounded-full p-0.5 shadow-sm"><Check className="w-3 h-3 text-white" /></div>
                  </div>
                )}

                <div className={`${viewMode === 'list' ? 'shrink-0' : 'w-full aspect-square flex items-center justify-center mb-2 shrink-0 bg-gray-50 rounded-2xl'}`}>
                  {file.thumb ? (
                     <img src={file.thumb} alt="" className="w-10 h-10 rounded-xl object-cover shadow-sm pointer-events-none" />
                  ) : (
                    <FileIcon isDir={file.is_dir} name={file.name} className={viewMode === 'list' ? 'w-8 h-8' : 'w-12 h-12'} />
                  )}
                </div>
                
                <div className={`min-w-0 ${viewMode === 'grid' ? 'w-full text-center' : 'flex-1'}`}>
                  <h3 className={`font-bold text-gray-800 break-words ${viewMode === 'list' ? 'text-sm truncate' : 'text-[11px] leading-tight line-clamp-2 px-1 h-7 overflow-hidden'}`}>
                    {file.name}
                  </h3>
                  {viewMode === 'list' && (
                    <p className="text-[10px] text-gray-400 mt-1 flex items-center gap-2 font-medium">
                      {!file.is_dir ? formatSize(file.size) : 'Folder'}
                      <span className="opacity-30">•</span>
                      {formatDate(file.modified)}
                    </p>
                  )}
                </div>
                {!file.is_dir && viewMode === 'list' && !isSelectionMode && (
                   <button 
                    onClick={(e) => { e.stopPropagation(); setSelectedFile({ file, path: (path === '/' ? '' : path) + '/' + file.name }); }}
                    className="p-2 hover:bg-gray-100 rounded-full text-gray-300 transition-colors"
                   >
                     <MoreVertical className="w-5 h-5" />
                   </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Global Toast with Modern Styling */}
      {toastMsg && (
        <div className="fixed bottom-28 left-1/2 -translate-x-1/2 z-[100] animate-in slide-in-from-bottom-8 duration-500 cubic-bezier(0.2, 1, 0.3, 1)">
          <div className="bg-gray-900/90 backdrop-blur-xl text-white px-7 py-3.5 rounded-[1.5rem] shadow-2xl flex items-center gap-3 border border-white/10 ring-1 ring-black/5">
            <Info className="w-4 h-4 text-indigo-400" />
            <span className="text-[11px] font-black uppercase tracking-[0.1em]">{toastMsg}</span>
          </div>
        </div>
      )}

      {/* FAB Container */}
      {!isSelectionMode && (
        <div className="fixed bottom-8 right-8 flex flex-col gap-4 z-30 pb-safe">
          <button 
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className="w-16 h-16 bg-indigo-600 text-white rounded-[1.8rem] shadow-2xl shadow-indigo-100 flex items-center justify-center active:scale-90 transition-all"
          >
            {uploading ? <RefreshCw className="w-7 h-7 animate-spin" /> : <Plus className="w-9 h-9" />}
          </button>
          <input type="file" ref={fileInputRef} className="hidden" onChange={async (e) => {
             const file = e.target.files?.[0]; if (!file) return;
             setUploading(true); try { await serviceRef.current.uploadFile(path, file); await fetchFiles(path, true); } catch (err: any) { setErrorMsg(`Upload failed: ${err.message}`); } finally { setUploading(false); }
          }} />
        </div>
      )}

      {path !== '/' && !isSelectionMode && (
        <button onClick={goBack} className="fixed bottom-8 left-8 w-14 h-14 bg-white text-indigo-600 rounded-[1.5rem] shadow-xl border border-indigo-50 flex items-center justify-center z-30 mb-safe ml-safe active:scale-90 transition-all">
          <ArrowLeft className="w-7 h-7" />
        </button>
      )}

      {/* Preview Sheet */}
      {selectedFile && <ActionSheet file={selectedFile.file} path={selectedFile.path} config={config} onClose={() => setSelectedFile(null)} onRefresh={() => fetchFiles(path, true)} />}
      
      {/* Batch Selection Bar */}
      {isSelectionMode && (
        <div className="fixed bottom-8 left-6 right-6 z-50 animate-in slide-in-from-bottom-6 duration-400">
          <div className="w-full bg-gray-900 text-white rounded-[2.2rem] p-4.5 shadow-2xl flex items-center justify-between border border-white/5 backdrop-blur-lg">
             <div className="flex items-center gap-3.5 ml-2">
                <button onClick={() => {setIsSelectionMode(false); setSelectedItemNames(new Set());}} className="p-2 hover:bg-white/10 rounded-full transition-colors"><X className="w-5 h-5" /></button>
                <div className="flex flex-col"><span className="text-sm font-black tracking-tight">{selectedItemNames.size}</span><span className="text-[8px] text-gray-400 font-bold uppercase tracking-[0.2em]">Selected</span></div>
             </div>
             <div className="flex gap-2.5">
                <button onClick={async () => {
                   setBatchActionLoading(true); try {
                     for (const name of selectedItemNames) {
                       const detail = await serviceRef.current.getFileDetail((path === '/' ? '' : path) + '/' + name);
                       const a = document.createElement('a'); a.href = detail.data.raw_url; a.download = name; a.click(); await new Promise(r => setTimeout(r, 600));
                     }
                     setIsSelectionMode(false); setSelectedItemNames(new Set());
                   } catch (err: any) { setErrorMsg(err.message); } finally { setBatchActionLoading(false); }
                }} className="p-3.5 bg-white/10 rounded-[1.2rem] hover:bg-white/20 transition-colors"><Download className="w-5 h-5" /></button>
                <button onClick={() => setShowBatchDeleteConfirm(true)} className="p-3.5 bg-red-600 rounded-[1.2rem] hover:bg-red-500 shadow-lg shadow-red-900/20 transition-colors"><Trash2 className="w-5 h-5" /></button>
             </div>
          </div>
        </div>
      )}

      {/* Delete Prompt */}
      {showBatchDeleteConfirm && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-6 bg-black/40 backdrop-blur-md animate-in fade-in duration-300">
          <div className="bg-white rounded-[2.8rem] p-9 shadow-2xl w-full max-w-sm animate-in zoom-in-95 duration-300">
             <div className="flex flex-col items-center text-center">
               <div className="w-16 h-16 bg-red-50 rounded-[1.8rem] flex items-center justify-center mb-7">
                  <Trash2 className="w-8 h-8 text-red-500" />
               </div>
               <h3 className="text-xl font-black text-gray-900 mb-3 tracking-tight">Delete {selectedItemNames.size} Items?</h3>
               <p className="text-[13px] text-gray-500 leading-relaxed mb-10 px-4">This action is permanent and cannot be reversed. Proceed with caution.</p>
               <div className="grid grid-cols-2 gap-4 w-full">
                 <button onClick={() => setShowBatchDeleteConfirm(false)} className="py-4.5 bg-gray-50 text-gray-600 rounded-[1.4rem] font-bold active:scale-95 transition-all">Cancel</button>
                 <button onClick={async () => {
                    setBatchActionLoading(true); try { await serviceRef.current.deleteFiles(path, Array.from(selectedItemNames)); await fetchFiles(path, true); setIsSelectionMode(false); setSelectedItemNames(new Set()); } catch (err: any) { setErrorMsg(err.message); } finally { setBatchActionLoading(false); setShowBatchDeleteConfirm(false); }
                 }} className="py-4.5 bg-red-600 text-white rounded-[1.4rem] font-bold shadow-xl shadow-red-100 active:scale-95 transition-all">Delete All</button>
               </div>
             </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default FileBrowser;
