import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  View,
  Text,
  TextInput,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
  RefreshControl,
} from 'react-native';
import { Button, Dialog, Portal, FAB } from 'react-native-paper';
import Clipboard from '@react-native-clipboard/clipboard';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { localFsService } from '../services/localFsService';
import { useAuthStore } from '../stores/authStore';
import { AListService } from '../services/alistService';
import { AListFile, SortKey, SortOrder, FilterType } from '../types';
import { downloadService } from '../services/downloadService';
import FileItem from '../components/FileItem';
import ActionSheet from '../components/ActionSheet';
import SortSheet from '../components/SortSheet';
import FilterSheet from '../components/FilterSheet';
import BatchActionBar from '../components/BatchActionBar';

const VIDEO_EXTS = ['mp4', 'mkv', 'avi', 'mov', 'flv', 'wmv', 'rmvb'];
const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'];
const DOC_EXTS = [
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'json',
  'yaml', 'yml', 'js', 'ts', 'py', 'css', 'html', 'conf', 'ini', 'log',
  'tsx', 'jsx', 'sh', 'sql', 'xml', 'csv', 'go', 'c', 'cpp', 'java',
];

const FileBrowserScreen = () => {
  const { config, logout } = useAuthStore();
  const [path, setPath] = useState('/');
  const [files, setFiles] = useState<AListFile[]>([]);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<'list' | 'grid'>('list');
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const [sortKey, setSortKey] = useState<SortKey>('name');
  const [sortOrder, setSortOrder] = useState<SortOrder>('asc');
  const [foldersFirst, setFoldersFirst] = useState(true);
  const [showSortSheet, setShowSortSheet] = useState(false);

  const [filterType, setFilterType] = useState<FilterType>('all');
  const [customExt, setCustomExt] = useState('');
  const [showFilterSheet, setShowFilterSheet] = useState(false);

  const [selectedFile, setSelectedFile] = useState<{ file: AListFile; path: string } | null>(null);
  const [selectedItemNames, setSelectedItemNames] = useState<Set<string>>(new Set());
  const [isSelectionMode, setIsSelectionMode] = useState(false);
  const [batchActionLoading, setBatchActionLoading] = useState(false);
  const [showBatchDeleteConfirm, setShowBatchDeleteConfirm] = useState(false);

  const [uploading, setUploading] = useState(false);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  const serviceRef = useRef<AListService | null>(null);
  if (config && !serviceRef.current) {
    serviceRef.current = new AListService(config);
  }

  useEffect(() => {
    (async () => {
      try {
        const sk = await AsyncStorage.getItem('alist_sort_key');
        const so = await AsyncStorage.getItem('alist_sort_order');
        const ff = await AsyncStorage.getItem('alist_folders_first');
        if (sk) setSortKey(sk as SortKey);
        if (so) setSortOrder(so as SortOrder);
        if (ff !== null) setFoldersFirst(ff !== 'false');
      } catch {}
    })();
  }, []);

  useEffect(() => {
    AsyncStorage.setItem('alist_sort_key', sortKey).catch(() => {});
    AsyncStorage.setItem('alist_sort_order', sortOrder).catch(() => {});
    AsyncStorage.setItem('alist_folders_first', String(foldersFirst)).catch(() => {});
  }, [sortKey, sortOrder, foldersFirst]);

  useEffect(() => {
    if (toastMsg) {
      const timer = setTimeout(() => setToastMsg(null), 2000);
      return () => clearTimeout(timer);
    }
  }, [toastMsg]);

  const fetchFiles = useCallback(async (currentPath: string, forceRefresh = false, pageNum = 1) => {
    if (!serviceRef.current) return;
    if (pageNum === 1) {
      setLoading(!forceRefresh);
    } else {
      setLoadingMore(true);
    }
    setErrorMsg(null);
    try {
      const response = await serviceRef.current.listFiles(currentPath, pageNum, 100, forceRefresh);
      const newFiles = response.data.content || [];
      
      if (pageNum === 1) {
        setFiles(newFiles);
      } else {
        setFiles(prev => [...prev, ...newFiles]);
      }
      
      setHasMore(newFiles.length === 100);
      setPage(pageNum);
      
      if (forceRefresh && pageNum === 1) setToastMsg('Sync Complete');
    } catch (err: any) {
      if (err.message?.includes('Unauthorized') || err.response?.status === 401) {
        logout();
      }
      setErrorMsg('Sync failed: ' + (err.message || 'Unknown error'));
    } finally {
      setLoading(false);
      setLoadingMore(false);
      setRefreshing(false);
    }
  }, [logout]);

  useEffect(() => {
    fetchFiles(path, false, 1);
    setIsSelectionMode(false);
    setSelectedItemNames(new Set());
  }, [path, fetchFiles]);

  const sortedAndFilteredFiles = useMemo(() => {
    let result = files.filter(f => {
      const matchesSearch = f.name.toLowerCase().includes(searchQuery.toLowerCase());
      if (!matchesSearch) return false;
      if (f.is_dir) return true;
      const ext = f.name.split('.').pop()?.toLowerCase() || '';
      switch (filterType) {
        case 'video': return VIDEO_EXTS.includes(ext);
        case 'image': return IMAGE_EXTS.includes(ext);
        case 'doc': return DOC_EXTS.includes(ext);
        case 'others': return !VIDEO_EXTS.includes(ext) && !IMAGE_EXTS.includes(ext) && !DOC_EXTS.includes(ext);
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

  const handleItemClick = (file: AListFile) => {
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

  const handleLongPress = (file: AListFile) => {
    if (!isSelectionMode) {
      setIsSelectionMode(true);
      setSelectedItemNames(new Set([file.name]));
    }
  };

  const handleBatchDownload = async () => {
    if (hasFolderSelected || !serviceRef.current || !config) return;
    setBatchActionLoading(true);
    try {
      const dirResult = await localFsService.pickDirectory();
      if (!dirResult) {
        setBatchActionLoading(false);
        return;
      }
      
      setToastMsg('Starting batch download...');
      for (const name of selectedItemNames) {
        const file = files.find(f => f.name === name);
        if (!file) continue;
        
        await downloadService.startDownload(
          config,
          (path === '/' ? '' : path) + '/' + name,
          dirResult.uri,
          file.size,
          'application/octet-stream'
        );
        // Small delay to prevent overwhelming the native bridge/service instantly
        await new Promise<void>(r => setTimeout(r, 100));
      }
      setIsSelectionMode(false);
      setSelectedItemNames(new Set());
    } catch (err: any) {
      if (err.code !== 'CANCELLED') {
        setErrorMsg(err.message || 'Batch download failed');
      }
    } finally {
      setBatchActionLoading(false);
    }
  };

  const handleBatchCopyLinks = async () => {
    if (hasFolderSelected || !serviceRef.current) return;
    setBatchActionLoading(true);
    try {
      const links: string[] = [];
      for (const name of selectedItemNames) {
        const detail = await serviceRef.current.getFileDetail((path === '/' ? '' : path) + '/' + name);
        links.push(detail.data.raw_url);
      }
      Clipboard.setString(links.join('\n'));
      setToastMsg(`Copied ${links.length} links`);
      setIsSelectionMode(false);
      setSelectedItemNames(new Set());
    } catch (err: any) {
      setErrorMsg(err.message);
    } finally {
      setBatchActionLoading(false);
    }
  };

  const handleBatchDelete = async () => {
    if (!serviceRef.current) return;
    setBatchActionLoading(true);
    try {
      await serviceRef.current.deleteFiles(path, Array.from(selectedItemNames));
      await fetchFiles(path, true, 1);
      setIsSelectionMode(false);
      setSelectedItemNames(new Set());
    } catch (err: any) {
      setErrorMsg(err.message);
    } finally {
      setBatchActionLoading(false);
      setShowBatchDeleteConfirm(false);
    }
  };

  const handleUpload = async () => {
    if (!serviceRef.current) return;
    try {
      const result = await localFsService.pickFile();
      if (result) {
        setUploading(true);
        await serviceRef.current.uploadFile(
          path, 
          result.uri, 
          result.name || 'file', 
          result.mimeType || 'application/octet-stream'
        );
        await fetchFiles(path, true, 1);
        setToastMsg('Upload complete');
      }
    } catch (err: any) {
      if (!err.message?.includes('cancelled') && err.code !== 'CANCELLED') {
        setErrorMsg(err.message || 'Upload failed');
      }
    } finally {
      setUploading(false);
    }
  };

  const pathParts = path.split('/').filter(Boolean);

  const renderItem = ({ item }: { item: AListFile }) => (
    <FileItem
      file={item}
      isSelected={selectedItemNames.has(item.name)}
      isSelectionMode={isSelectionMode}
      viewMode={viewMode}
      onPress={() => handleItemClick(item)}
      onLongPress={() => handleLongPress(item)}
      onMorePress={() => setSelectedFile({ file: item, path: (path === '/' ? '' : path) + '/' + item.name })}
    />
  );

  return (
    <View style={styles.container}>
      {/* Toolbar */}
      <View style={styles.toolbar}>
        {isSelectionMode && (
          <TouchableOpacity
            style={styles.selectAllButton}
            onPress={() => {
              if (selectedItemNames.size === sortedAndFilteredFiles.length) {
                setSelectedItemNames(new Set());
                setIsSelectionMode(false);
              } else {
                setSelectedItemNames(new Set(sortedAndFilteredFiles.map(f => f.name)));
              }
            }}
          >
            <Text style={styles.selectAllIcon}>
              {selectedItemNames.size === sortedAndFilteredFiles.length ? '☑' : '☐'}
            </Text>
          </TouchableOpacity>
        )}
        <View style={styles.searchContainer}>
          <TextInput
            style={styles.searchInput}
            placeholder="Search files..."
            value={searchQuery}
            onChangeText={setSearchQuery}
            placeholderTextColor="#aaa"
          />
        </View>
        <View style={styles.toolbarActions}>
          <TouchableOpacity
            style={styles.toolbarButton}
            onPress={() => setShowLogoutConfirm(true)}
          >
            <Text style={styles.toolbarIcon}>🚪</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.toolbarButton, filterType !== 'all' && styles.toolbarButtonActive]}
            onPress={() => setShowFilterSheet(true)}
          >
            <Text style={styles.toolbarIcon}>⊞</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.toolbarButton}
            onPress={() => setShowSortSheet(true)}
          >
            <Text style={styles.toolbarIcon}>⇅</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.toolbarButton}
            onPress={() => setViewMode(v => v === 'list' ? 'grid' : 'list')}
          >
            <Text style={styles.toolbarIcon}>{viewMode === 'list' ? '▦' : '☰'}</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Breadcrumbs */}
      <View style={styles.breadcrumbs}>
        <TouchableOpacity onPress={() => handleNavigate('/')}>
          <Text style={styles.breadcrumbHome}>⌂</Text>
        </TouchableOpacity>
        {pathParts.map((crumb, idx) => (
          <React.Fragment key={idx}>
            <Text style={styles.breadcrumbSeparator}>›</Text>
            <TouchableOpacity onPress={() => handleNavigate('/' + pathParts.slice(0, idx + 1).join('/'))}>
              <Text style={[styles.breadcrumbText, idx === pathParts.length - 1 && styles.breadcrumbActive]}>
                {crumb}
              </Text>
            </TouchableOpacity>
          </React.Fragment>
        ))}
      </View>

      {/* File List */}
      {loading && !refreshing ? (
        <View style={styles.loaderContainer}>
          <ActivityIndicator size="large" color="#6750a4" />
          <Text style={styles.loaderText}>Scanning...</Text>
        </View>
      ) : (
        <FlatList
          data={sortedAndFilteredFiles}
          renderItem={renderItem}
          keyExtractor={item => item.name}
          key={viewMode}
          numColumns={viewMode === 'grid' ? 3 : 1}
          contentContainerStyle={styles.fileList}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={() => {
                setRefreshing(true);
                fetchFiles(path, true, 1);
              }}
              colors={['#6750a4']}
            />
          }
          onEndReached={() => {
            if (hasMore && !loadingMore && !loading) {
              fetchFiles(path, false, page + 1);
            }
          }}
          onEndReachedThreshold={0.5}
          ListFooterComponent={
            loadingMore ? (
              <View style={styles.loadingMoreContainer}>
                <ActivityIndicator size="small" color="#6750a4" />
              </View>
            ) : null
          }
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyIcon}>∅</Text>
              <Text style={styles.emptyText}>No results</Text>
            </View>
          }
        />
      )}

      {/* FAB: Upload */}
      {!isSelectionMode && (
        <FAB
          icon="plus"
          style={styles.uploadFab}
          onPress={handleUpload}
          loading={uploading}
          disabled={uploading}
          color="white"
        />
      )}

      {/* Back FAB */}
      {path !== '/' && !isSelectionMode && (
        <FAB
          icon="arrow-left"
          style={styles.backFab}
          onPress={goBack}
          color="#6750a4"
          small
        />
      )}

      {/* ActionSheet for selected file */}
      {selectedFile && config && (
        <ActionSheet
          file={selectedFile.file}
          path={selectedFile.path}
          config={config}
          onClose={() => setSelectedFile(null)}
          onRefresh={() => fetchFiles(path, true, 1)}
        />
      )}

      {/* Sort Sheet */}
      <SortSheet
        visible={showSortSheet}
        sortKey={sortKey}
        sortOrder={sortOrder}
        foldersFirst={foldersFirst}
        onSortKeyChange={setSortKey}
        onSortOrderChange={setSortOrder}
        onFoldersFirstChange={setFoldersFirst}
        onClose={() => setShowSortSheet(false)}
      />

      {/* Filter Sheet */}
      <FilterSheet
        visible={showFilterSheet}
        filterType={filterType}
        customExt={customExt}
        onFilterTypeChange={setFilterType}
        onCustomExtChange={setCustomExt}
        onClose={() => setShowFilterSheet(false)}
      />

      {/* Batch Action Bar */}
      {isSelectionMode && (
        <BatchActionBar
          selectedCount={selectedItemNames.size}
          hasFolderSelected={hasFolderSelected}
          onBatchDownload={handleBatchDownload}
          onBatchCopyLinks={handleBatchCopyLinks}
          onBatchDelete={() => setShowBatchDeleteConfirm(true)}
          onExitSelectionMode={() => {
            setIsSelectionMode(false);
            setSelectedItemNames(new Set());
          }}
          batchActionLoading={batchActionLoading}
        />
      )}

      {/* Batch Delete Confirm */}
      <Portal>
        <Dialog visible={showBatchDeleteConfirm} onDismiss={() => setShowBatchDeleteConfirm(false)}>
          <Dialog.Title>Delete {selectedItemNames.size} Items?</Dialog.Title>
          <Dialog.Content>
            <Text>Permanent action. Cannot be reversed.</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setShowBatchDeleteConfirm(false)}>Cancel</Button>
            <Button onPress={handleBatchDelete} textColor="#b3261e" loading={batchActionLoading}>
              Delete
            </Button>
          </Dialog.Actions>
        </Dialog>

        <Dialog visible={showLogoutConfirm} onDismiss={() => setShowLogoutConfirm(false)}>
          <Dialog.Title>Logout?</Dialog.Title>
          <Dialog.Content>
            <Text>Are you sure you want to disconnect from {config?.serverName || 'this server'}?</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setShowLogoutConfirm(false)}>Cancel</Button>
            <Button onPress={logout} textColor="#b3261e">Logout</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>

      {/* Toast */}
      {toastMsg && (
        <View style={styles.toast}>
          <Text style={styles.toastText}>{toastMsg}</Text>
        </View>
      )}

      {/* Error Toast */}
      {errorMsg && (
        <View style={styles.errorToast}>
          <Text style={styles.errorToastText}>{errorMsg}</Text>
          <TouchableOpacity onPress={() => setErrorMsg(null)}>
            <Text style={styles.errorToastDismiss}>X</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f7f2fa',
  },
  toolbar: {
    backgroundColor: 'white',
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingTop: 48,
  },
  selectAllButton: {
    backgroundColor: '#6750a4',
    padding: 10,
    borderRadius: 20,
  },
  selectAllIcon: {
    color: 'white',
    fontSize: 18,
  },
  searchContainer: {
    flex: 1,
    minHeight: 0,
  },
  searchInput: {
    backgroundColor: '#f0f0f0',
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    fontSize: 14,
    fontWeight: '500',
    color: '#333',
  },
  toolbarActions: {
    flexDirection: 'row',
    gap: 4,
  },
  toolbarButton: {
    backgroundColor: '#f0f0f0',
    padding: 10,
    borderRadius: 20,
  },
  toolbarButtonActive: {
    backgroundColor: '#ede7f6',
  },
  toolbarIcon: {
    fontSize: 18,
    color: '#666',
  },
  breadcrumbs: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: 'rgba(255,255,255,0.5)',
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
    gap: 4,
  },
  breadcrumbHome: {
    fontSize: 16,
    color: '#999',
    padding: 4,
  },
  breadcrumbSeparator: {
    fontSize: 16,
    color: '#ddd',
  },
  breadcrumbText: {
    fontSize: 14,
    color: '#888',
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  breadcrumbActive: {
    fontWeight: '700',
    color: '#6750a4',
    backgroundColor: '#ede7f6',
    borderRadius: 8,
  },
  loaderContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 12,
  },
  loaderText: {
    fontSize: 14,
    color: '#999',
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  loadingMoreContainer: {
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fileList: {
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 120,
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 60,
  },
  emptyIcon: {
    fontSize: 48,
    color: '#ddd',
    marginBottom: 16,
  },
  emptyText: {
    fontSize: 14,
    fontWeight: '900',
    color: '#ddd',
    textTransform: 'uppercase',
  },
  uploadFab: {
    position: 'absolute',
    bottom: 32,
    right: 24,
    backgroundColor: '#6750a4',
    borderRadius: 28,
  },
  backFab: {
    position: 'absolute',
    bottom: 32,
    left: 24,
    backgroundColor: 'white',
    borderRadius: 20,
  },
  toast: {
    position: 'absolute',
    bottom: 100,
    left: '50%',
    transform: [{ translateX: -100 }],
    backgroundColor: 'rgba(0,0,0,0.85)',
    paddingHorizontal: 24,
    paddingVertical: 14,
    borderRadius: 24,
    zIndex: 100,
  },
  toastText: {
    color: 'white',
    fontSize: 11,
    fontWeight: '900',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  errorToast: {
    position: 'absolute',
    bottom: 96,
    left: 16,
    right: 16,
    backgroundColor: '#b3261e',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderRadius: 16,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    zIndex: 50,
  },
  errorToastText: {
    color: 'white',
    fontSize: 12,
    fontWeight: '600',
    flex: 1,
  },
  errorToastDismiss: {
    color: 'white',
    fontWeight: '900',
    fontSize: 16,
    marginLeft: 12,
  },
});

export default FileBrowserScreen;
