
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
  Tag,
  X,
  Trash2,
  Copy,
  CheckSquare,
  Square,
  Download,
  Info,
  Calendar,
  Database
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
  
  // Menu visibility states
  const [isSortMenuOpen, setIsSortMenuOpen] = useState(false);
  const [isFilterMenuOpen, setIsFilterMenuOpen] = useState(false);
  
  // Pull-to-refresh state
  const [pullDistance, setPullDistance] = useState(0);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [toastMsg, setToastMsg] = useState<string | null>(null);
  
  const PULL_LIMIT = 110; 
  const PULL_TRIGGER_ZONE = 100; 
  const DAMPING_COEFFICIENT = 0.65; 
  
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  // Selection State
  const [selectedItemNames, setSelectedItemNames] = useState<Set<string>>(new Set());
  const [isSelectionMode, setIsSelectionMode] = useState(false);
  const [batchActionLoading, setBatchActionLoading] = useState(false);
  const [showBatchDeleteConfirm, setShowBatchDeleteConfirm] = useState(false);

  // Filter State
  const [filterType, setFilterType] = useState<FilterType>('all');
  const [customExt, setCustomExt] = useState('');

  // Sorting Preferences
  const [sortKey, setSortKey] = useState<SortKey>(() => (localStorage.getItem('alist_sort_key') as SortKey) || 'name');
  const [sortOrder, setSortOrder] = useState<SortOrder>(() => (localStorage.getItem('alist_sort_order') as SortOrder) || 'asc');
  const [foldersFirst, setFoldersFirst] = useState<boolean>(() => localStorage.getItem('alist_folders_first') !== 'false');

  const touchStartPos = useRef({ x: 0, y: 0 });
  const touchStartTime = useRef(0);
  const longPressTimer = useRef<number | null>(null);
  const wasLongPress = useRef(false);
  const isPulling = useRef(false);
  
  const serviceRef = useRef(new AListService(config));
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (toastMsg) {
      const timer = setTimeout(() => setToastMsg(null), 2000);
      return () => clearTimeout(timer);
    }
  }, [toastMsg]);

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
      if (forceRefresh) setToastMsg("Sync Complete");
    } catch (err: any) {
      if (err.message.includes('Unauthorized')) onSessionExpired();
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

  const sortedAndFilteredFiles = useMemo(() => {
    const videoExts = ['mp4', 'mkv', 'avi', 'mov', 'flv', 'wmv', 'rmvb'];
    const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'];
    const docExts = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'json', 'yaml', 'yml', 'js', 'ts', 'py', 'css', 'html', 'conf', 'ini', 'log', 'tsx', 'jsx', 'sh', 'sql', 'xml', 'csv', 'go', 'c', 'cpp', 'java'];

    let result = files.filter(f => {
      const matchesSearch = f.name.toLowerCase().includes(searchQuery.toLowerCase());
      if (!matchesSearch) return false;
      if (f.is_dir) return true;
      const ext = f.name.split('.').pop()?.toLowerCase() || '';
      switch (filterType) {
        case 'video': return videoExts.includes(ext);
        case 'image': return imageExts.includes(ext);
        case 'doc': return docExts.includes(ext);
        case 'others': return !videoExts.includes(ext) && !imageExts.includes(ext) && !docExts.includes(ext);
        case 'custom': return customExt ? ext === customExt.toLowerCase().trim().replace('.', '') : true;
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
        case 'name': comparison = a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' }); break;
        case 'size': comparison = (a.size || 0) - (b.size || 0); break;
        case 'modified': comparison = new Date(a.modified).getTime() - new Date(b.modified).getTime(); break;
      }
      return sortOrder === 'asc' ? comparison : -comparison;
    });

    return result;
  }, [files, searchQuery, sortKey, sortOrder, foldersFirst, filterType, customExt]);

  const hasFolderSelected = useMemo(() => {
    return Array.from(selectedItemNames).some(name => files.find(f => f.name === name)?.is_dir);
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

  const handleTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length !== 1) return;
    touchStartPos.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
    touchStartTime.current = Date.now();
    isPulling.current = scrollContainerRef.current?.scrollTop === 0;
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    if (e.touches.length !== 1) return;
    const currentY = e.touches[0].clientY;
    const currentX = e.touches[0].clientX;
    const deltaY = currentY - touchStartPos.current.y;
    const deltaX = Math.abs(currentX - touchStartPos.current.x);

    // Optimization: If the finger moves significantly (12px), it's a swipe/scroll gesture, 
    // so we MUST cancel any pending long-press selection timer.
    if (Math.abs(deltaY) > 12 || deltaX > 12) {
      if (longPressTimer.current) {
        clearTimeout(longPressTimer.current);
        longPressTimer.current = null;
      }
    }

    if (!isPulling.current || isRefreshing || isSelectionMode) return;

    if (deltaY > 0 && deltaY > deltaX) {
      let rawDistance = Math.pow(deltaY, 0.8) * DAMPING_COEFFICIENT * 2;
      const finalDistance = Math.min(rawDistance, PULL_LIMIT);
      setPullDistance(finalDistance);
      if (finalDistance >= PULL_TRIGGER_ZONE && pullDistance < PULL_TRIGGER_ZONE) {
         if ('vibrate' in navigator) navigator.vibrate(12);
      }
      if (e.cancelable) e.preventDefault();
    } else {
      setPullDistance(0);
    }
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (e.changedTouches.length !== 1) return;
    if (pullDistance >= PULL_TRIGGER_ZONE && !isRefreshing) {
      setIsRefreshing(true);
      setPullDistance(50);
      setToastMsg("Refreshing list...");
      fetchFiles(path, true);
    } else {
      setPullDistance(0);
    }
    isPulling.current = false;
  };

  const handleItemClick = (file: AListFile) => {
    if (wasLongPress.current) { wasLongPress.current = false; return; }
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
    if (file.is_dir) handleNavigate((path === '/' ? '' : path) + '/' + file.name);
    else setSelectedFile({ file, path: (path === '/' ? '' : path) + '/' + file.name });
  };

  const handleBatchDownload = async () => {
    if (hasFolderSelected) return;
    setBatchActionLoading(true);
    setToastMsg("Starting batch download...");
    try {
      for (const name of selectedItemNames) {
        const detail = await serviceRef.current.getFileDetail((path === '/' ? '' : path) + '/' + name);
        const a = document.createElement('a');
        a.href = detail.data.raw_url;
        a.download = name;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        await new Promise(r => setTimeout(r, 600));
      }
      setIsSelectionMode(false);
      setSelectedItemNames(new Set());
    } catch (err: any) { setErrorMsg(err.message); } finally { setBatchActionLoading(false); }
  };

  const handleBatchCopyLinks = async () => {
    if (hasFolderSelected) return;
    setBatchActionLoading(true);
    try {
      const links = [];
      for (const name of selectedItemNames) {
        const detail = await serviceRef.current.getFileDetail((path === '/' ? '' : path) + '/' + name);
        links.push(detail.data.raw_url);
      }
      await navigator.clipboard.writeText(links.join('\n'));
      setToastMsg(`Copied ${links.length} links`);
      setIsSelectionMode(false);
      setSelectedItemNames(new Set());
    } catch (err: any) { setErrorMsg(err.message); } finally { setBatchActionLoading(false); }
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

  return (
    <div 
      className="h-full flex flex-col bg-[#f7f2fa] relative overflow-hidden"
      style={{ touchAction: 'pan-y' }}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
    >
      {/* PTR UI */}
      <div 
        className="absolute left-0 right-0 flex justify-center z-50 pointer-events-none"
        style={{ 
          top: 0, 
          transform: `translateY(${Math.max(pullDistance - 55, -55)}px)`,
          opacity: pullDistance > 10 ? 1 : 0,
          transition: isRefreshing ? 'transform 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275)' : 'none'
        }}
      >
        <div className={`flex items-center gap-2 bg-white rounded-full px-5 py-2.5 shadow-2xl border transition-all duration-300 ${pullDistance >= PULL_TRIGGER_ZONE ? 'border-indigo-500 scale-105' : 'border-gray-100 scale-95'}`}>
          <RefreshCw className={`w-5 h-5 transition-colors ${isRefreshing ? 'animate-spin text-indigo-600' : pullDistance >= PULL_TRIGGER_ZONE ? 'text-indigo-600' : 'text-gray-300'}`} style={{ transform: isRefreshing ? undefined : `rotate(${pullDistance * 4}deg)` }} />
          {pullDistance >= PULL_TRIGGER_ZONE && !isRefreshing && <span className="text-[10px] font-black text-indigo-600 uppercase tracking-widest">Sync Data</span>}
        </div>
      </div>

      {/* Toolbar */}
      <div className="bg-white border-b border-gray-100 z-10 shadow-sm shrink-0">
        <div className="px-4 py-3 flex gap-2 items-center">
          {isSelectionMode && (
            <button onClick={() => { if (selectedItemNames.size === sortedAndFilteredFiles.length) { setSelectedItemNames(new Set()); setIsSelectionMode(false); } else setSelectedItemNames(new Set(sortedAndFilteredFiles.map(f => f.name))); }} className="p-2.5 rounded-2xl bg-indigo-600 text-white shrink-0 shadow-lg shadow-indigo-100"><CheckSquare className="w-5 h-5" /></button>
          )}
          <div className="relative flex-1 min-w-0">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input type="text" placeholder="Search files..." className="w-full pl-9 pr-4 py-2.5 bg-gray-100 border-none rounded-2xl text-sm outline-none font-medium" value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} />
          </div>
          <div className="flex gap-1 shrink-0">
            <button onClick={() => setIsFilterMenuOpen(true)} className={`p-2.5 rounded-2xl border transition-all ${filterType !== 'all' ? 'bg-indigo-50 border-indigo-200 text-indigo-600' : 'bg-gray-100 border-transparent text-gray-600'}`}><Filter className="w-5 h-5" /></button>
            <button onClick={() => setIsSortMenuOpen(true)} className="p-2.5 bg-gray-100 border-transparent rounded-2xl text-gray-600 transition-all"><ArrowUpDown className="w-5 h-5" /></button>
            <button onClick={() => setViewMode(v => v === 'list' ? 'grid' : 'list')} className="p-2.5 bg-gray-100 border-transparent rounded-2xl text-gray-600 transition-all">{viewMode === 'list' ? <LayoutGrid className="w-5 h-5" /> : <List className="w-5 h-5" />}</button>
          </div>
        </div>
      </div>

      {/* Breadcrumbs */}
      <div className="flex items-center gap-1 px-4 py-3 overflow-x-auto whitespace-nowrap hide-scrollbar bg-white/50 backdrop-blur-sm border-b border-gray-100 text-sm z-10 shrink-0">
        <button onClick={() => handleNavigate('/')} className="p-1 text-gray-400"><Home className="w-4 h-4" /></button>
        {path.split('/').filter(Boolean).map((crumb, idx, arr) => (
          <React.Fragment key={idx}>
            <ChevronRight className="w-4 h-4 text-gray-300 shrink-0" />
            <button onClick={() => handleNavigate('/' + arr.slice(0, idx + 1).join('/'))} className={`px-1.5 py-0.5 rounded-lg ${idx === arr.length - 1 ? 'font-bold text-indigo-600 bg-indigo-50' : 'text-gray-500'}`}>{crumb}</button>
          </React.Fragment>
        ))}
      </div>

      {/* File List */}
      <div ref={scrollContainerRef} className="flex-1 overflow-y-auto px-4 pt-2 pb-32 transition-transform ease-out" style={{ transform: pullDistance > 0 ? `translateY(${pullDistance * 0.85}px)` : undefined, transitionDuration: isRefreshing ? '400ms' : '0ms' }}>
        {loading && !isRefreshing ? (
          <div className="h-60 flex flex-col items-center justify-center gap-3"><RefreshCw className="w-10 h-10 text-indigo-600 animate-spin" /><span className="text-sm text-gray-400 font-bold uppercase">Scanning...</span></div>
        ) : sortedAndFilteredFiles.length === 0 ? (
          <div className="h-60 flex flex-col items-center justify-center text-gray-300"><Search className="w-16 h-16 mb-4 opacity-30" /><p className="text-sm font-black uppercase">No results</p></div>
        ) : (
          <div className={viewMode === 'list' ? 'flex flex-col gap-1.5' : 'grid grid-cols-3 sm:grid-cols-4 gap-4'}>
            {sortedAndFilteredFiles.map((file) => (
              <div key={file.name} onPointerDown={(e) => { 
                wasLongPress.current = false; 
                longPressTimer.current = window.setTimeout(() => { 
                  // Extra guard: do not enter selection mode if currently pulling or refreshing or if finger has moved
                  if (!isSelectionMode && !isRefreshing && pullDistance === 0) { 
                    setIsSelectionMode(true); 
                    setSelectedItemNames(new Set([file.name])); 
                    wasLongPress.current = true; 
                    if ('vibrate' in navigator) navigator.vibrate(50); 
                  } 
                }, 800); 
              }} onPointerUp={() => { if (longPressTimer.current) { clearTimeout(longPressTimer.current); longPressTimer.current = null; } }} onClick={() => handleItemClick(file)}
              className={`relative group transition-all active:scale-[0.96] overflow-hidden select-none ${viewMode === 'list' ? 'flex items-center gap-4 p-3.5 rounded-[1.5rem] border shadow-sm' : 'flex flex-col items-center p-3.5 rounded-[1.5rem] border shadow-sm'} ${selectedItemNames.has(file.name) ? 'bg-indigo-50 border-indigo-200' : 'bg-white border-transparent'}`}>
                {isSelectionMode && selectedItemNames.has(file.name) && <div className="absolute top-2.5 left-2.5 z-10 bg-indigo-600 rounded-full p-0.5"><Check className="w-3 h-3 text-white" /></div>}
                <div className={`${viewMode === 'list' ? 'shrink-0' : 'w-full aspect-square flex items-center justify-center mb-2 bg-gray-50 rounded-2xl'}`}>{file.thumb ? <img src={file.thumb} className="w-10 h-10 rounded-xl object-cover" /> : <FileIcon isDir={file.is_dir} name={file.name} className={viewMode === 'list' ? 'w-8 h-8' : 'w-12 h-12'} />}</div>
                <div className={`min-w-0 ${viewMode === 'grid' ? 'w-full text-center' : 'flex-1'}`}>
                  <h3 className={`font-bold text-gray-800 break-words ${viewMode === 'list' ? 'text-sm truncate' : 'text-[11px] leading-tight line-clamp-2'}`}>{file.name}</h3>
                  {viewMode === 'list' && <p className="text-[10px] text-gray-400 mt-1 font-medium">{!file.is_dir ? formatSize(file.size) : 'Folder'} • {formatDate(file.modified)}</p>}
                </div>
                {!file.is_dir && viewMode === 'list' && !isSelectionMode && <button onClick={(e) => { e.stopPropagation(); setSelectedFile({ file, path: (path === '/' ? '' : path) + '/' + file.name }); }} className="p-2 text-gray-300"><MoreVertical className="w-5 h-5" /></button>}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Sorting Sheet */}
      {isSortMenuOpen && (
        <div className="fixed inset-0 z-[60] flex items-end justify-center animate-in fade-in duration-200">
          <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setIsSortMenuOpen(false)} />
          <div className="relative w-full max-w-lg bg-white rounded-t-[2.5rem] p-8 animate-in slide-in-from-bottom-full duration-300 pb-safe">
            <div className="w-12 h-1.5 bg-gray-100 rounded-full mx-auto mb-8" />
            <h3 className="text-xl font-black mb-6 text-gray-900 px-2 flex items-center gap-2"><ArrowUpDown className="w-5 h-5 text-indigo-600" /> Sort Files</h3>
            <div className="space-y-6">
              <div className="grid grid-cols-3 gap-3">
                {(['name', 'modified', 'size'] as SortKey[]).map(k => (
                  <button key={k} onClick={() => setSortKey(k)} className={`py-4 rounded-2xl text-xs font-bold transition-all border ${sortKey === k ? 'bg-indigo-600 text-white border-transparent' : 'bg-gray-50 text-gray-500 border-gray-100'}`}>
                    <span className="capitalize">{k}</span>
                  </button>
                ))}
              </div>
              <div className="flex bg-gray-100 p-1.5 rounded-[1.5rem] gap-1.5">
                <button onClick={() => setSortOrder('asc')} className={`flex-1 flex items-center justify-center gap-2 py-3 rounded-[1.2rem] text-xs font-black transition-all ${sortOrder === 'asc' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-400'}`}><ArrowUpNarrowWide className="w-4 h-4" /> Ascending</button>
                <button onClick={() => setSortOrder('desc')} className={`flex-1 flex items-center justify-center gap-2 py-3 rounded-[1.2rem] text-xs font-black transition-all ${sortOrder === 'desc' ? 'bg-white text-indigo-600 shadow-sm' : 'text-gray-400'}`}><ArrowDownWideNarrow className="w-4 h-4" /> Descending</button>
              </div>
              <button onClick={() => setFoldersFirst(!foldersFirst)} className="w-full flex items-center justify-between p-5 bg-gray-50 rounded-2xl border border-gray-100 group transition-all">
                <div className="flex items-center gap-3"><FolderTree className={`w-5 h-5 ${foldersFirst ? 'text-indigo-600' : 'text-gray-400'}`} /><span className="text-sm font-bold text-gray-700">Folders First</span></div>
                <div className={`w-12 h-6 rounded-full transition-colors relative ${foldersFirst ? 'bg-indigo-600' : 'bg-gray-300'}`}><div className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-all ${foldersFirst ? 'right-1' : 'left-1'}`} /></div>
              </button>
              <button onClick={() => setIsSortMenuOpen(false)} className="w-full py-4.5 bg-indigo-600 text-white rounded-2xl font-bold shadow-lg shadow-indigo-100 active:scale-95 transition-all">Done</button>
            </div>
          </div>
        </div>
      )}

      {/* Filter Sheet */}
      {isFilterMenuOpen && (
        <div className="fixed inset-0 z-[60] flex items-end justify-center animate-in fade-in duration-200">
          <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setIsFilterMenuOpen(false)} />
          <div className="relative w-full max-w-lg bg-white rounded-t-[2.5rem] p-8 animate-in slide-in-from-bottom-full duration-300 pb-safe">
            <div className="w-12 h-1.5 bg-gray-100 rounded-full mx-auto mb-8" />
            <h3 className="text-xl font-black mb-6 text-gray-900 px-2 flex items-center gap-2"><Filter className="w-5 h-5 text-indigo-600" /> Filter Types</h3>
            <div className="grid grid-cols-2 gap-3 mb-6">
              {[
                { id: 'all', icon: FileBox, label: 'All Files' },
                { id: 'video', icon: FileVideo, label: 'Videos' },
                { id: 'image', icon: FileImage, label: 'Images' },
                { id: 'doc', icon: FileText, label: 'Documents' },
                { id: 'others', icon: Hash, label: 'Others' },
                { id: 'custom', icon: Tag, label: 'Custom Ext' }
              ].map(f => (
                <button key={f.id} onClick={() => setFilterType(f.id as FilterType)} className={`flex items-center gap-3 p-4 rounded-2xl border transition-all ${filterType === f.id ? 'bg-indigo-50 border-indigo-200 text-indigo-600' : 'bg-gray-50 border-gray-100 text-gray-500'}`}>
                  <f.icon className="w-4 h-4" /><span className="text-xs font-bold">{f.label}</span>
                </button>
              ))}
            </div>
            {filterType === 'custom' && (
              <div className="mb-6 animate-in slide-in-from-top-2">
                <input type="text" placeholder="Extension (e.g. apk, iso)" className="w-full px-5 py-4 bg-gray-50 border border-gray-100 rounded-2xl text-sm font-bold outline-none focus:ring-2 focus:ring-indigo-500" value={customExt} onChange={(e) => setCustomExt(e.target.value)} />
              </div>
            )}
            <button onClick={() => setIsFilterMenuOpen(false)} className="w-full py-4.5 bg-indigo-600 text-white rounded-2xl font-bold active:scale-95 transition-all">Apply Filter</button>
          </div>
        </div>
      )}

      {/* Other Modals (Delete, Multi-select Bar, Action Sheet, Toast) */}
      {selectedFile && <ActionSheet file={selectedFile.file} path={selectedFile.path} config={config} onClose={() => setSelectedFile(null)} onRefresh={() => fetchFiles(path, true)} />}
      
      {isSelectionMode && (
        <div className="fixed bottom-8 left-6 right-6 z-50 animate-in slide-in-from-bottom-6">
          <div className="w-full bg-gray-900 text-white rounded-[2.2rem] p-4 shadow-2xl flex items-center justify-between border border-white/5 backdrop-blur-lg">
             <div className="flex items-center gap-3.5 ml-1">
                <button onClick={() => {setIsSelectionMode(false); setSelectedItemNames(new Set());}} className="p-2 hover:bg-white/10 rounded-full transition-colors"><X className="w-5 h-5" /></button>
                <div className="flex flex-col"><span className="text-sm font-black tracking-tight">{selectedItemNames.size}</span><span className="text-[8px] text-gray-400 font-bold uppercase tracking-[0.2em]">Selected</span></div>
             </div>
             <div className="flex gap-1.5">
                <button onClick={handleBatchDownload} disabled={batchActionLoading || hasFolderSelected} className={`p-3.5 rounded-[1.2rem] transition-all active:scale-95 ${hasFolderSelected ? 'bg-gray-800 text-gray-600' : 'bg-white/10 text-white hover:bg-white/20'}`} title="Batch Download"><Download className="w-5 h-5" /></button>
                <button onClick={handleBatchCopyLinks} disabled={batchActionLoading || hasFolderSelected} className={`p-3.5 rounded-[1.2rem] transition-all active:scale-95 ${hasFolderSelected ? 'bg-gray-800 text-gray-600' : 'bg-white/10 text-white hover:bg-white/20'}`} title="Batch Copy Links"><Copy className="w-5 h-5" /></button>
                <button onClick={() => setShowBatchDeleteConfirm(true)} disabled={batchActionLoading} className="p-3.5 bg-red-600 text-white rounded-[1.2rem] hover:bg-red-500 transition-all active:scale-95" title="Batch Delete"><Trash2 className="w-5 h-5" /></button>
             </div>
          </div>
          {hasFolderSelected && <div className="mt-2 text-center"><span className="text-[9px] font-black text-orange-400 uppercase tracking-widest bg-gray-900/50 px-3 py-1 rounded-full">Batch download/link disabled for folders</span></div>}
        </div>
      )}

      {showBatchDeleteConfirm && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-6 bg-black/40 backdrop-blur-md animate-in fade-in duration-300">
          <div className="bg-white rounded-[2.8rem] p-9 shadow-2xl w-full max-w-sm animate-in zoom-in-95 duration-300">
             <div className="flex flex-col items-center text-center">
               <div className="w-16 h-16 bg-red-50 rounded-[1.8rem] flex items-center justify-center mb-7"><Trash2 className="w-8 h-8 text-red-500" /></div>
               <h3 className="text-xl font-black text-gray-900 mb-3 tracking-tight">Delete {selectedItemNames.size} Items?</h3>
               <p className="text-[13px] text-gray-500 leading-relaxed mb-10 px-4">Permanent action. Cannot be reversed.</p>
               <div className="grid grid-cols-2 gap-4 w-full">
                 <button onClick={() => setShowBatchDeleteConfirm(false)} className="py-4.5 bg-gray-50 text-gray-600 rounded-[1.4rem] font-bold">Cancel</button>
                 <button onClick={async () => { setBatchActionLoading(true); try { await serviceRef.current.deleteFiles(path, Array.from(selectedItemNames)); await fetchFiles(path, true); setIsSelectionMode(false); setSelectedItemNames(new Set()); } catch (err: any) { setErrorMsg(err.message); } finally { setBatchActionLoading(false); setShowBatchDeleteConfirm(false); } }} className="py-4.5 bg-red-600 text-white rounded-[1.4rem] font-bold">Delete</button>
               </div>
             </div>
          </div>
        </div>
      )}

      {toastMsg && (
        <div className="fixed bottom-28 left-1/2 -translate-x-1/2 z-[100] animate-in slide-in-from-bottom-8 duration-500 cubic-bezier(0.2, 1, 0.3, 1)">
          <div className="bg-gray-900/90 backdrop-blur-xl text-white px-7 py-3.5 rounded-[1.5rem] shadow-2xl flex items-center gap-3 border border-white/10">
            <Info className="w-4 h-4 text-indigo-400" />
            <span className="text-[11px] font-black uppercase tracking-[0.1em]">{toastMsg}</span>
          </div>
        </div>
      )}

      {!isSelectionMode && (
        <div className="fixed bottom-8 right-8 flex flex-col gap-4 z-30 pb-safe">
          <button onClick={() => fileInputRef.current?.click()} disabled={uploading} className="w-16 h-16 bg-indigo-600 text-white rounded-[1.8rem] shadow-2xl flex items-center justify-center active:scale-90 transition-all">{uploading ? <RefreshCw className="w-7 h-7 animate-spin" /> : <Plus className="w-9 h-9" />}</button>
          <input type="file" ref={fileInputRef} className="hidden" onChange={async (e) => { const file = e.target.files?.[0]; if (!file) return; setUploading(true); try { await serviceRef.current.uploadFile(path, file); await fetchFiles(path, true); } catch (err: any) { setErrorMsg(err.message); } finally { setUploading(false); } }} />
        </div>
      )}

      {path !== '/' && !isSelectionMode && <button onClick={goBack} className="fixed bottom-8 left-8 w-14 h-14 bg-white text-indigo-600 rounded-[1.5rem] shadow-xl border border-indigo-50 flex items-center justify-center active:scale-90 transition-all"><ArrowLeft className="w-7 h-7" /></button>}
    </div>
  );
};

export default FileBrowser;
