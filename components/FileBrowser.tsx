
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
  Download
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
  
  const serviceRef = useRef(new AListService(config));
  const fileInputRef = useRef<HTMLInputElement>(null);

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

  // Derived state for batch actions
  const hasFolderSelected = useMemo(() => {
    return Array.from(selectedItemNames).some(name => {
      const file = files.find(f => f.name === name);
      return file?.is_dir;
    });
  }, [selectedItemNames, files]);

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

  // Multi-Selection Handlers
  const toggleSelection = (name: string) => {
    setSelectedItemNames(prev => {
      const next = new Set(prev);
      if (next.has(name)) {
        next.delete(name);
        if (next.size === 0) setIsSelectionMode(false);
      } else {
        next.add(name);
      }
      return next;
    });
  };

  const selectAll = () => {
    if (selectedItemNames.size === sortedAndFilteredFiles.length) {
      setSelectedItemNames(new Set());
      setIsSelectionMode(false);
    } else {
      setSelectedItemNames(new Set(sortedAndFilteredFiles.map(f => f.name)));
    }
  };

  const handleBatchDelete = async () => {
    setBatchActionLoading(true);
    try {
      await serviceRef.current.deleteFiles(path, Array.from(selectedItemNames));
      await fetchFiles(path, true);
      setIsSelectionMode(false);
      setSelectedItemNames(new Set());
    } catch (err: any) {
      setErrorMsg(`Batch delete failed: ${err.message}`);
    } finally {
      setBatchActionLoading(false);
      setShowBatchDeleteConfirm(false);
    }
  };

  const handleBatchCopyLinks = async () => {
    if (hasFolderSelected || selectedItemNames.size === 0) return;
    setBatchActionLoading(true);
    try {
      const links = [];
      for (const name of selectedItemNames) {
        const fullPath = (path === '/' ? '' : path) + '/' + name;
        const detail = await serviceRef.current.getFileDetail(fullPath);
        links.push(detail.data.raw_url);
      }
      await navigator.clipboard.writeText(links.join('\n'));
      alert(`Successfully copied ${links.length} download links!`);
      setIsSelectionMode(false);
      setSelectedItemNames(new Set());
    } catch (err: any) {
      setErrorMsg(`Batch link retrieval failed: ${err.message}`);
    } finally {
      setBatchActionLoading(false);
    }
  };

  const handleBatchDownload = async () => {
    if (hasFolderSelected || selectedItemNames.size === 0) return;
    setBatchActionLoading(true);
    try {
      for (const name of selectedItemNames) {
        const fullPath = (path === '/' ? '' : path) + '/' + name;
        const detail = await serviceRef.current.getFileDetail(fullPath);
        const a = document.createElement('a');
        a.href = detail.data.raw_url;
        a.download = name;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        await new Promise(r => setTimeout(r, 500));
      }
      setIsSelectionMode(false);
      setSelectedItemNames(new Set());
    } catch (err: any) {
      setErrorMsg(`Batch download failed: ${err.message}`);
    } finally {
      setBatchActionLoading(false);
    }
  };

  // Gesture Handlers
  const handlePointerDown = (e: React.PointerEvent, file: AListFile) => {
    wasLongPress.current = false;
    touchStartPos.current = { x: e.clientX, y: e.clientY };
    touchStartTime.current = Date.now();

    longPressTimer.current = window.setTimeout(() => {
      if (!isSelectionMode) {
        setIsSelectionMode(true);
        toggleSelection(file.name);
        wasLongPress.current = true;
        if ('vibrate' in navigator) navigator.vibrate(50);
      }
    }, 1000);
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
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (e.changedTouches.length !== 1) return;
    const deltaX = e.changedTouches[0].clientX - touchStartPos.current.x;
    const deltaY = e.changedTouches[0].clientY - touchStartPos.current.y;
    const duration = Date.now() - touchStartTime.current;

    // Detect edge swipe back (Only when NOT in selection mode)
    if (!isSelectionMode && touchStartPos.current.x < 40 && deltaX > 100 && Math.abs(deltaX) > Math.abs(deltaY) * 2 && duration < 500) {
      goBack();
    }
  };

  const handleItemClick = (file: AListFile) => {
    if (wasLongPress.current) {
      wasLongPress.current = false;
      return;
    }

    if (isSelectionMode) {
      toggleSelection(file.name);
      return;
    }

    if (file.is_dir) {
      handleNavigate((path === '/' ? '' : path) + '/' + file.name);
    } else {
      setSelectedFile({ file, path: (path === '/' ? '' : path) + '/' + file.name });
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
      setErrorMsg(`Upload failed: ${err.message}`);
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

  const breadcrumbs = path.split('/').filter(Boolean);

  const filterChips: { id: FilterType, label: string, icon: any }[] = [
    { id: 'all', label: 'All Files', icon: Filter },
    { id: 'video', label: 'Videos', icon: FileVideo },
    { id: 'image', label: 'Images', icon: FileImage },
    { id: 'doc', label: 'Documents', icon: FileText },
    { id: 'others', label: 'Others', icon: FileBox },
    { id: 'custom', label: 'By Extension', icon: Hash },
  ];

  return (
    <div 
      className="h-full flex flex-col bg-[#f7f2fa] relative overflow-hidden"
      style={{ touchAction: 'pan-y' }}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      {/* Search & Toolbar Area */}
      <div className="bg-white border-b border-gray-100 z-10 shadow-sm">
        <div className="px-4 py-3 flex gap-2 items-center">
          {/* Select All Button - Appears on the left only in selection mode */}
          {isSelectionMode && (
            <button 
              onClick={selectAll}
              className="p-2.5 rounded-2xl transition-all active:scale-95 bg-indigo-600 text-white border border-transparent shrink-0 animate-in slide-in-from-left-2 duration-200"
              title="Select All"
            >
              {selectedItemNames.size === sortedAndFilteredFiles.length ? <CheckSquare className="w-5 h-5" /> : <Square className="w-5 h-5" />}
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
            {searchQuery && (
              <button 
                onClick={() => setSearchQuery('')}
                className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-gray-400 hover:text-gray-600"
              >
                <X className="w-3 h-3" />
              </button>
            )}
          </div>

          <div className="flex gap-1 shrink-0">
            <button 
              onClick={() => setIsFilterMenuOpen(true)}
              className={`p-2.5 rounded-2xl transition-all active:scale-95 border ${filterType !== 'all' ? 'bg-indigo-50 border-indigo-200 text-indigo-600' : 'bg-gray-100 border-transparent text-gray-600'}`}
              title="Filter"
            >
              <Filter className="w-5 h-5" />
            </button>
            <button 
              onClick={() => setIsSortMenuOpen(true)}
              className={`p-2.5 rounded-2xl transition-all active:scale-95 border ${isSortMenuOpen ? 'bg-indigo-100 border-indigo-200 text-indigo-600' : 'bg-gray-100 border-transparent text-gray-600'}`}
              title="Sort"
            >
              <ArrowUpDown className="w-5 h-5" />
            </button>
            <button 
              onClick={() => setViewMode(v => v === 'list' ? 'grid' : 'list')}
              className="p-2.5 bg-gray-100 rounded-2xl text-gray-600 hover:bg-gray-200 active:scale-95 transition-all border border-transparent"
              title="View Mode"
            >
              {viewMode === 'list' ? <LayoutGrid className="w-5 h-5" /> : <List className="w-5 h-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Breadcrumbs */}
      <div className="flex items-center gap-1 px-4 py-3 overflow-x-auto whitespace-nowrap hide-scrollbar bg-white/50 backdrop-blur-sm border-b border-gray-100 text-sm z-10">
        <button 
          onClick={() => handleNavigate('/')}
          className="p-1 hover:text-indigo-600 flex items-center text-gray-400"
        >
          <Home className="w-4 h-4" />
        </button>
        {breadcrumbs.map((crumb, idx) => (
          <React.Fragment key={idx}>
            <ChevronRight className="w-4 h-4 text-gray-300 shrink-0" />
            <button 
              onClick={() => handleNavigate('/' + breadcrumbs.slice(0, idx + 1).join('/'))}
              className={`hover:text-indigo-600 px-1 rounded-md transition-colors ${idx === breadcrumbs.length - 1 ? 'font-bold text-indigo-600 bg-indigo-50/50' : 'text-gray-500'}`}
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
          <p className="flex-1">{errorMsg}</p>
          <button onClick={() => setErrorMsg(null)} className="p-1 font-bold opacity-50">✕</button>
        </div>
      )}

      {/* File List */}
      <div className="flex-1 overflow-y-auto px-4 pt-2 pb-32 touch-pan-y">
        {loading ? (
          <div className="h-60 flex flex-col items-center justify-center gap-3">
            <div className="w-10 h-10 border-4 border-indigo-100 border-t-indigo-600 rounded-full animate-spin" />
            <span className="text-sm text-gray-400 font-bold uppercase tracking-widest">Scanning...</span>
          </div>
        ) : sortedAndFilteredFiles.length === 0 ? (
          <div className="h-60 flex flex-col items-center justify-center text-gray-400 opacity-50 scale-110 grayscale">
             <Search className="w-16 h-16 mb-4" />
             <p className="text-sm font-medium">Nothing matches your filters</p>
             <button 
              onClick={() => {setFilterType('all'); setSearchQuery(''); setCustomExt('');}}
              className="mt-4 text-indigo-600 text-xs font-bold bg-indigo-50 px-4 py-2 rounded-full active:scale-95 transition-all"
             >
              Clear All Filters
             </button>
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
                  relative group transition-all active:scale-[0.96] overflow-hidden select-none touch-none
                  ${viewMode === 'list' 
                    ? 'flex items-center gap-4 p-3 rounded-[1.5rem] border transition-colors shadow-sm' 
                    : 'flex flex-col items-center p-3 rounded-[1.5rem] border transition-colors shadow-sm'
                  }
                  ${selectedItemNames.has(file.name) 
                    ? 'bg-indigo-50 border-indigo-200' 
                    : 'bg-white border-transparent hover:border-indigo-100'
                  }
                `}
              >
                {isSelectionMode && (
                  <div className={`absolute top-2 left-2 z-10 transition-transform ${selectedItemNames.has(file.name) ? 'scale-100' : 'scale-0'}`}>
                    <div className="bg-indigo-600 rounded-full p-0.5">
                      <Check className="w-3 h-3 text-white" />
                    </div>
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
                  <h3 className={`
                    font-bold text-gray-800 break-words
                    ${viewMode === 'list' 
                      ? 'text-sm truncate' 
                      : 'text-[11px] leading-tight line-clamp-2 h-7 overflow-hidden px-1'
                    }
                  `}>
                    {file.name}
                  </h3>
                  {viewMode === 'list' && (
                    <p className="text-[10px] text-gray-400 mt-1 flex items-center gap-2 font-medium">
                      {!file.is_dir && <span>{formatSize(file.size)}</span>}
                      {file.is_dir && <span>Folder</span>}
                      <span className="opacity-30">•</span>
                      <span>{formatDate(file.modified)}</span>
                    </p>
                  )}
                </div>

                {!file.is_dir && viewMode === 'list' && !isSelectionMode && (
                   <button 
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelectedFile({ file, path: (path === '/' ? '' : path) + '/' + file.name });
                    }}
                    className="p-2 hover:bg-gray-50 rounded-full text-gray-300 transition-colors"
                   >
                     <MoreVertical className="w-5 h-5" />
                   </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Multi-Select Action Bar */}
      {isSelectionMode && (
        <div className="fixed bottom-6 left-6 right-6 z-50 flex flex-col items-center pb-safe">
           <div className="w-full bg-gray-900 text-white rounded-[2rem] p-4 shadow-2xl flex items-center justify-between gap-2 animate-in slide-in-from-bottom-4">
              <div className="flex items-center gap-2 ml-2">
                <button 
                  onClick={() => {setIsSelectionMode(false); setSelectedItemNames(new Set());}}
                  className="p-1 hover:bg-white/10 rounded-full transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
                <div className="flex flex-col">
                   <span className="text-xs font-black">{selectedItemNames.size}</span>
                   <span className="text-[7px] text-gray-400 font-bold uppercase tracking-widest">Selected</span>
                </div>
              </div>

              <div className="flex gap-2">
                {/* Download Button */}
                <button 
                  onClick={handleBatchDownload}
                  disabled={batchActionLoading || hasFolderSelected || selectedItemNames.size === 0}
                  className={`p-3 rounded-2xl transition-all active:scale-95 disabled:opacity-30 flex items-center gap-2 ${hasFolderSelected ? 'bg-gray-700 text-gray-500' : 'bg-white/10 hover:bg-white/20'}`}
                  title="Batch Download"
                >
                  {batchActionLoading ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Download className="w-5 h-5" />}
                </button>

                {/* Copy Links Button */}
                <button 
                  onClick={handleBatchCopyLinks}
                  disabled={batchActionLoading || hasFolderSelected || selectedItemNames.size === 0}
                  className={`p-3 rounded-2xl transition-all active:scale-95 disabled:opacity-30 flex items-center gap-2 ${hasFolderSelected ? 'bg-gray-700 text-gray-500' : 'bg-white/10 hover:bg-white/20'}`}
                  title="Copy Direct Links"
                >
                  {batchActionLoading ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Copy className="w-5 h-5" />}
                </button>

                {/* Delete Button */}
                <button 
                  onClick={() => setShowBatchDeleteConfirm(true)}
                  disabled={batchActionLoading || selectedItemNames.size === 0}
                  className="p-3 bg-red-600 hover:bg-red-500 rounded-2xl transition-all active:scale-95 disabled:opacity-30"
                  title="Batch Delete"
                >
                  <Trash2 className="w-5 h-5" />
                </button>
              </div>
           </div>
           
           {hasFolderSelected && (
              <div className="mt-2 bg-gray-800/80 backdrop-blur px-3 py-1.5 rounded-full animate-in slide-in-from-top-1">
                 <p className="text-[9px] text-orange-300 font-black uppercase tracking-widest">
                   Link/Download disabled when folders are selected
                 </p>
              </div>
           )}
        </div>
      )}

      {/* Batch Delete Confirmation Overlay */}
      {showBatchDeleteConfirm && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-6 bg-black/40 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white rounded-[2.5rem] p-8 shadow-2xl w-full max-w-sm animate-in zoom-in-95 duration-200">
            <div className="flex flex-col items-center text-center">
              <div className="w-16 h-16 bg-red-50 rounded-3xl flex items-center justify-center mb-6">
                <Trash2 className="w-8 h-8 text-red-500" />
              </div>
              <h3 className="text-xl font-black text-gray-900 mb-3">Delete {selectedItemNames.size} Items?</h3>
              <p className="text-sm text-gray-500 leading-relaxed mb-8">
                Are you sure you want to permanently delete these items? This action cannot be reversed.
              </p>
              <div className="grid grid-cols-2 gap-4 w-full">
                <button
                  onClick={() => setShowBatchDeleteConfirm(false)}
                  className="py-4 bg-gray-50 text-gray-600 rounded-2xl text-sm font-black active:scale-95 transition-all"
                >
                  Cancel
                </button>
                <button
                  onClick={handleBatchDelete}
                  className="py-4 bg-red-600 text-white rounded-2xl text-sm font-black shadow-lg shadow-red-100 active:scale-95 transition-all"
                >
                  Delete All
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Filter Menu Overlay */}
      {isFilterMenuOpen && (
        <div className="fixed inset-0 z-50 flex items-end justify-center">
          <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px] animate-in fade-in" onClick={() => setIsFilterMenuOpen(false)} />
          <div className="relative w-full max-w-lg bg-white rounded-t-[3rem] p-8 shadow-2xl animate-in slide-in-from-bottom duration-300 pb-safe">
            <div className="w-12 h-1.5 bg-gray-100 rounded-full mx-auto mb-8" />
            
            <h3 className="text-xl font-black text-gray-900 mb-8 px-2 flex items-center gap-3">
              <Filter className="w-6 h-6 text-indigo-600" />
              Filter Files
            </h3>

            <div className="space-y-6">
              <div className="space-y-3">
                <p className="text-[10px] font-black text-gray-300 uppercase tracking-[0.2em] px-2">Select Category</p>
                <div className="grid grid-cols-2 gap-2">
                  {filterChips.map((chip) => (
                    <button
                      key={chip.id}
                      onClick={() => setFilterType(chip.id)}
                      className={`py-4 rounded-2xl text-xs font-bold transition-all flex items-center gap-4 px-5 ${filterType === chip.id ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-100' : 'bg-gray-50 text-gray-500 hover:bg-gray-100'}`}
                    >
                      <chip.icon className={`w-4 h-4 ${filterType === chip.id ? 'text-white' : 'text-gray-400'}`} />
                      {chip.label}
                      {filterType === chip.id && <Check className="w-4 h-4 ml-auto" />}
                    </button>
                  ))}
                </div>
              </div>

              {filterType === 'custom' && (
                <div className="space-y-3 animate-in slide-in-from-top-2 duration-200">
                  <p className="text-[10px] font-black text-gray-300 uppercase tracking-[0.2em] px-2">Enter Extension</p>
                  <div className="relative">
                    <Hash className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                    <input 
                      autoFocus
                      type="text"
                      placeholder="e.g. rar, zip, iso..."
                      className="w-full pl-12 pr-4 py-4 bg-gray-50 border border-gray-100 rounded-2xl focus:ring-2 focus:ring-indigo-500 outline-none font-bold text-gray-800"
                      value={customExt}
                      onChange={(e) => setCustomExt(e.target.value)}
                    />
                  </div>
                </div>
              )}

              <button 
                onClick={() => setIsFilterMenuOpen(false)}
                className="w-full py-5 bg-gray-900 text-white rounded-[2rem] font-black text-sm active:scale-[0.98] transition-all shadow-xl shadow-gray-200 mt-4"
              >
                Show Results
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Sort Menu Overlay */}
      {isSortMenuOpen && (
        <div className="fixed inset-0 z-50 flex items-end justify-center">
          <div className="absolute inset-0 bg-black/40 backdrop-blur-[2px] animate-in fade-in" onClick={() => setIsSortMenuOpen(false)} />
          <div className="relative w-full max-w-lg bg-white rounded-t-[3rem] p-8 shadow-2xl animate-in slide-in-from-bottom duration-300 pb-safe">
            <div className="w-12 h-1.5 bg-gray-100 rounded-full mx-auto mb-8" />
            
            <h3 className="text-xl font-black text-gray-900 mb-8 px-2 flex items-center gap-3">
              <ArrowUpDown className="w-6 h-6 text-indigo-600" />
              Sort Options
            </h3>

            <div className="space-y-6">
              {/* Sort By Section */}
              <div className="space-y-3">
                <p className="text-[10px] font-black text-gray-300 uppercase tracking-[0.2em] px-2">Sort Files By</p>
                <div className="grid grid-cols-3 gap-2">
                  {[
                    { key: 'name', label: 'Name' },
                    { key: 'modified', label: 'Date' },
                    { key: 'size', label: 'Size' }
                  ].map((item) => (
                    <button
                      key={item.key}
                      onClick={() => setSortKey(item.key as SortKey)}
                      className={`py-4 rounded-2xl text-xs font-bold transition-all flex flex-col items-center gap-2 ${sortKey === item.key ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-100' : 'bg-gray-50 text-gray-500'}`}
                    >
                      {item.label}
                      {sortKey === item.key && <Check className="w-3 h-3" />}
                    </button>
                  ))}
                </div>
              </div>

              {/* Order Section */}
              <div className="space-y-3">
                <p className="text-[10px] font-black text-gray-300 uppercase tracking-[0.2em] px-2">Sort Direction</p>
                <div className="flex gap-2">
                  <button
                    onClick={() => setSortOrder('asc')}
                    className={`flex-1 py-4 rounded-2xl text-xs font-bold flex items-center justify-center gap-2 transition-all ${sortOrder === 'asc' ? 'bg-indigo-50 text-indigo-600 border border-indigo-100' : 'bg-gray-50 text-gray-500 border border-transparent'}`}
                  >
                    <ArrowUpNarrowWide className="w-4 h-4" />
                    Ascending
                  </button>
                  <button
                    onClick={() => setSortOrder('desc')}
                    className={`flex-1 py-4 rounded-2xl text-xs font-bold flex items-center justify-center gap-2 transition-all ${sortOrder === 'desc' ? 'bg-indigo-50 text-indigo-600 border border-indigo-100' : 'bg-gray-50 text-gray-500 border border-transparent'}`}
                  >
                    <ArrowDownWideNarrow className="w-4 h-4" />
                    Descending
                  </button>
                </div>
              </div>

              {/* Toggles */}
              <div className="space-y-3 pt-4">
                <div 
                  onClick={() => setFoldersFirst(!foldersFirst)}
                  className="flex items-center justify-between p-5 bg-gray-50 rounded-[2rem] active:bg-gray-100 transition-colors cursor-pointer group"
                >
                  <div className="flex items-center gap-4">
                    <div className={`p-3 rounded-2xl transition-colors ${foldersFirst ? 'bg-indigo-100 text-indigo-600' : 'bg-white text-gray-400'}`}>
                      <FolderTree className="w-5 h-5" />
                    </div>
                    <div>
                      <p className="text-sm font-bold text-gray-800">Folders always first</p>
                      <p className="text-[10px] text-gray-400 font-medium">Keep folders grouped at the top</p>
                    </div>
                  </div>
                  <div className={`w-12 h-6 rounded-full relative transition-colors ${foldersFirst ? 'bg-indigo-600' : 'bg-gray-200'}`}>
                    <div className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-all ${foldersFirst ? 'left-7' : 'left-1'}`} />
                  </div>
                </div>
              </div>

              <button 
                onClick={() => setIsSortMenuOpen(false)}
                className="w-full py-5 bg-gray-900 text-white rounded-[2rem] font-black text-sm active:scale-[0.98] transition-all shadow-xl shadow-gray-200 mt-4"
              >
                Apply Settings
              </button>
            </div>
          </div>
        </div>
      )}

      {/* FAB Container */}
      {!isSelectionMode && (
        <div className="fixed bottom-6 right-6 flex flex-col gap-4 z-30 pb-safe">
          <button 
            onClick={handleUploadClick}
            disabled={uploading}
            className="w-14 h-14 bg-indigo-600 text-white rounded-[1.5rem] shadow-xl flex items-center justify-center active:scale-90 transition-all disabled:opacity-50"
          >
            {uploading ? <RefreshCw className="w-6 h-6 animate-spin" /> : <Plus className="w-8 h-8" />}
          </button>
          <input type="file" ref={fileInputRef} className="hidden" onChange={handleFileChange} />
        </div>
      )}

      {/* Back Button */}
      {path !== '/' && !isSelectionMode && (
        <button 
          onClick={goBack}
          className="fixed bottom-6 left-6 w-12 h-12 bg-white text-indigo-600 rounded-[1.2rem] shadow-lg border border-indigo-50 flex items-center justify-center active:scale-90 transition-all z-30 mb-safe ml-safe"
        >
          <ArrowLeft className="w-6 h-6" />
        </button>
      )}

      {/* Status Overlay */}
      {uploading && (
        <div className="fixed bottom-24 right-6 bg-indigo-600 text-white px-5 py-3 rounded-2xl shadow-xl flex items-center gap-3 z-40 mb-safe animate-bounce">
          <Upload className="w-4 h-4" />
          <span className="text-[10px] font-black uppercase tracking-widest">Uploading</span>
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
