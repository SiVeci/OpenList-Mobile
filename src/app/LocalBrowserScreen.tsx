import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
  RefreshControl,
} from 'react-native';
import { Button, Dialog, Portal } from 'react-native-paper';
import FileIcon from '../components/FileIcon';
import { localFsService } from '../services/localFsService';
import { SAFFileInfo } from '../native/NativeSAF';

const LocalBrowserScreen = () => {
  const [currentDirUri, setCurrentDirUri] = useState<string | null>(null);
  const [currentDirName, setCurrentDirName] = useState('Local Storage');
  const [files, setFiles] = useState<SAFFileInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [_error, setError] = useState<string | null>(null);
  const [dirStack, setDirStack] = useState<{ uri: string; name: string }[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedFile, setSelectedFile] = useState<SAFFileInfo | null>(null);
  const [deleting, setDeleting] = useState(false);

  const loadRoot = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await localFsService.pickDirectory();
      setCurrentDirUri(result.uri);
      setCurrentDirName(result.name);
      setDirStack([]);
      const items = await localFsService.listFiles(result.uri);
      setFiles(items);
    } catch (err: any) {
      if (err.message?.includes('cancel') || err.code === 'CANCELLED') {
        setError(null);
      } else {
        setError(err.message || 'Failed to open directory');
      }
    } finally {
      setLoading(false);
    }
  };

  const loadDirectory = useCallback(async (uri: string) => {
    setLoading(true);
    setError(null);
    try {
      const items = await localFsService.listFiles(uri);
      setFiles(items);
      setCurrentDirUri(uri);
    } catch (err: any) {
      setError(err.message || 'Failed to list files');
    } finally {
      setLoading(false);
    }
  }, []);

  const navigateInto = (file: SAFFileInfo) => {
    if (!file.is_dir) return;
    if (currentDirUri) {
      setDirStack(prev => [...prev, { uri: currentDirUri, name: currentDirName }]);
    }
    setCurrentDirName(file.name);
    loadDirectory(file.uri);
  };

  const handleNavigateCrumb = (index: number) => {
    if (index === dirStack.length) return; // already here
    if (index === -1) {
      // Root
      if (dirStack.length > 0) {
        const root = dirStack[0];
        setDirStack([]);
        setCurrentDirName(root.name);
        loadDirectory(root.uri);
      }
      return;
    }
    const target = dirStack[index];
    setDirStack(s => s.slice(0, index));
    setCurrentDirName(target.name);
    loadDirectory(target.uri);
  };

  const handleChangeDir = () => {
    loadRoot();
  };

  const formatSize = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const formatDate = (timestamp: number) => {
    if (timestamp === 0) return '';
    const d = new Date(timestamp);
    return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const handleDelete = async () => {
    if (!selectedFile) return;
    setDeleting(true);
    try {
      await localFsService.deleteFile(selectedFile.uri);
      setSelectedFile(null);
      if (currentDirUri) {
        loadDirectory(currentDirUri);
      }
    } catch (err: any) {
      setError(err.message || 'Delete failed');
    } finally {
      setDeleting(false);
    }
  };

  const renderItem = ({ item }: { item: SAFFileInfo }) => (
    <TouchableOpacity
      style={styles.fileItem}
      onPress={() => {
        if (item.is_dir) navigateInto(item);
        else setSelectedFile(item);
      }}
      activeOpacity={0.7}
    >
      <View style={styles.iconContainer}>
        <FileIcon isDir={item.is_dir} name={item.name} size={32} />
      </View>
      <View style={styles.fileInfo}>
        <Text style={styles.fileName} numberOfLines={1}>{item.name}</Text>
        <Text style={styles.fileMeta}>
          {item.is_dir ? 'Folder' : formatSize(item.size)}
          {formatDate(item.modified) ? ` · ${formatDate(item.modified)}` : ''}
        </Text>
      </View>
      {item.is_dir && <Text style={styles.chevron}>›</Text>}
    </TouchableOpacity>
  );

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.headerContent}>
          <Text style={styles.headerTitle}>Local Files</Text>
          {currentDirUri && (
            <TouchableOpacity onPress={handleChangeDir} style={styles.changeDirButton}>
              <Text style={styles.changeDirText}>Change Dir</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>

      {currentDirUri && (
        <View style={styles.breadcrumbs}>
          <TouchableOpacity onPress={() => handleNavigateCrumb(-1)}>
            <Text style={[styles.breadcrumbText, dirStack.length === 0 && styles.breadcrumbActive]}>
              {dirStack.length > 0 ? dirStack[0].name : currentDirName}
            </Text>
          </TouchableOpacity>
          {dirStack.map((crumb, idx) => {
            if (idx === 0) return null; // skip root
            return (
              <React.Fragment key={idx}>
                <Text style={styles.breadcrumbSeparator}>›</Text>
                <TouchableOpacity onPress={() => handleNavigateCrumb(idx)}>
                  <Text style={styles.breadcrumbText}>{crumb.name}</Text>
                </TouchableOpacity>
              </React.Fragment>
            );
          })}
          {dirStack.length > 0 && (
            <>
              <Text style={styles.breadcrumbSeparator}>›</Text>
              <Text style={[styles.breadcrumbText, styles.breadcrumbActive]}>
                {currentDirName}
              </Text>
            </>
          )}
        </View>
      )}

      {!currentDirUri ? (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyIcon}>📂</Text>
          <Text style={styles.emptyTitle}>Browse Local Files</Text>
          <Text style={styles.emptySubtitle}>
            Select a directory to browse your device's file system
          </Text>
          <Button
            mode="contained"
            onPress={loadRoot}
            style={styles.openButton}
            buttonColor="#6750a4"
          >
            Open Directory
          </Button>
        </View>
      ) : loading ? (
        <View style={styles.loaderContainer}>
          <ActivityIndicator size="large" color="#6750a4" />
          <Text style={styles.loaderText}>Loading...</Text>
        </View>
      ) : (
        <FlatList
          data={files}
          renderItem={renderItem}
          keyExtractor={(item, idx) => `${item.documentId}_${idx}`}
          contentContainerStyle={styles.fileList}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={() => {
                if (currentDirUri) {
                  setRefreshing(true);
                  localFsService.listFiles(currentDirUri).then(setFiles).catch(() => {}).finally(() => setRefreshing(false));
                }
              }}
              colors={['#6750a4']}
            />
          }
          ListEmptyComponent={
            <View style={styles.emptyList}>
              <Text style={styles.emptyListText}>Empty directory</Text>
            </View>
          }
        />
      )}
      
      <Portal>
        <Dialog visible={!!selectedFile} onDismiss={() => setSelectedFile(null)}>
          <Dialog.Title>Delete File?</Dialog.Title>
          <Dialog.Content>
            <Text>Are you sure you want to delete {selectedFile?.name}?</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setSelectedFile(null)}>Cancel</Button>
            <Button onPress={handleDelete} textColor="#b3261e" loading={deleting}>
              Delete
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>

      {_error && (
        <View style={styles.errorToast}>
          <Text style={styles.errorToastText}>{_error}</Text>
          <TouchableOpacity onPress={() => setError(null)}>
            <Text style={styles.errorToastDismiss}>X</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f7f2fa' },
  header: {
    backgroundColor: 'white',
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
    paddingTop: 48,
    paddingBottom: 12,
    paddingHorizontal: 16,
  },
  headerContent: { flexDirection: 'row', alignItems: 'center' },
  backButton: { marginRight: 12, padding: 4 },
  backText: { fontSize: 28, color: '#6750a4', fontWeight: '300' },
  headerTitle: { fontSize: 18, fontWeight: '700', color: '#1d1d1d', flex: 1 },
  emptyContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 32 },
  emptyIcon: { fontSize: 64, marginBottom: 16 },
  emptyTitle: { fontSize: 20, fontWeight: '900', color: '#333', marginBottom: 8 },
  emptySubtitle: { fontSize: 14, color: '#888', textAlign: 'center', marginBottom: 24, lineHeight: 20 },
  openButton: { borderRadius: 16, paddingHorizontal: 24 },
  loaderContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', gap: 12 },
  loaderText: { fontSize: 14, color: '#999', fontWeight: '600' },
  fileList: { paddingHorizontal: 16, paddingTop: 8, paddingBottom: 32 },
  fileItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'white',
    borderRadius: 24,
    padding: 14,
    marginBottom: 6,
  },
  iconContainer: { marginRight: 16 },
  fileInfo: { flex: 1, minWidth: 0 },
  fileName: { fontSize: 14, fontWeight: '700', color: '#333' },
  fileMeta: { fontSize: 10, color: '#999', marginTop: 2, fontWeight: '500' },
  chevron: { fontSize: 24, color: '#ddd', fontWeight: '300' },
  emptyList: { alignItems: 'center', paddingVertical: 60 },
  emptyListText: { fontSize: 14, color: '#ccc', fontWeight: '700' },
  changeDirButton: { backgroundColor: '#f0f0f0', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16 },
  changeDirText: { fontSize: 12, fontWeight: '700', color: '#6750a4' },
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
  breadcrumbSeparator: { fontSize: 16, color: '#ddd' },
  breadcrumbText: { fontSize: 14, color: '#888', paddingHorizontal: 6, paddingVertical: 2 },
  breadcrumbActive: { fontWeight: '700', color: '#6750a4', backgroundColor: '#ede7f6', borderRadius: 8 },
  errorToast: {
    position: 'absolute',
    bottom: 32,
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
  errorToastText: { color: 'white', fontSize: 12, fontWeight: '600', flex: 1 },
  errorToastDismiss: { color: 'white', fontWeight: '900', fontSize: 16, marginLeft: 12 },
});

export default LocalBrowserScreen;
