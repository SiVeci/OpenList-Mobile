import React, { useEffect } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
} from 'react-native';
import { Button, ProgressBar } from 'react-native-paper';
import { downloadService } from '../services/downloadService';
import { useAppStore } from '../stores/appStore';

const DownloadScreen = () => {
  const { activeDownloads, updateDownload, clearDownloads } = useAppStore();

  useEffect(() => {
    downloadService.initListeners(
      (fileName, prog) => {
        updateDownload(fileName, { progress: prog, status: 'downloading' });
      },
      (fileName, uri) => {
        updateDownload(fileName, { progress: 100, status: 'complete', uri });
      },
      (fileName, error) => {
        updateDownload(fileName, { progress: 0, status: `error: ${error}` });
      },
      (fileName) => {
        const d = useAppStore.getState().activeDownloads[fileName];
        if (d && d.status === 'downloading') {
          updateDownload(fileName, { progress: d.progress, status: 'cancelled' });
        }
      },
    );

    return () => {
      downloadService.removeAllListeners();
    };
  }, [updateDownload]);

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const _handleDownload = async () => {
    // Left as placeholder if UI triggered manual download is needed
  };

  const downloadsArray = Object.values(activeDownloads).reverse();

  const handleCancel = (fileName: string) => {
    downloadService.cancelDownload(fileName);
  };

  const renderItem = ({ item }: { item: { fileName: string; progress: number; status: string } }) => (
    <View style={styles.downloadItem}>
      <View style={styles.downloadHeader}>
        <Text style={styles.fileName} numberOfLines={1}>{item.fileName}</Text>
        <Text style={styles.statusTextItem}>{item.status}</Text>
      </View>
      <ProgressBar 
        progress={item.progress / 100} 
        color={item.status === 'complete' ? '#4caf50' : item.status.startsWith('error') ? '#f44336' : '#6750a4'} 
        style={styles.progressBar} 
      />
      <View style={styles.progressRow}>
        <Text style={styles.progressPercent}>{item.progress}%</Text>
        {item.status === 'downloading' && (
          <TouchableOpacity onPress={() => handleCancel(item.fileName)} style={styles.cancelButton}>
            <Text style={styles.cancelButtonText}>Cancel</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Downloads</Text>
        {downloadsArray.length > 0 && (
          <Button mode="text" onPress={clearDownloads} compact>Clear</Button>
        )}
      </View>

      <View style={styles.content}>
        {downloadsArray.length > 0 ? (
          <FlatList
            data={downloadsArray}
            renderItem={renderItem}
            keyExtractor={item => item.fileName}
            contentContainerStyle={styles.listContent}
          />
        ) : (
          <View style={styles.idleContainer}>
            <Text style={styles.idleIcon}>↓</Text>
            <Text style={styles.idleTitle}>Download Manager</Text>
            <Text style={styles.idleSubtitle}>
              Active and recent downloads will appear here. Trigger a download from the remote file browser.
            </Text>
          </View>
        )}
      </View>
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
    paddingBottom: 16,
    paddingHorizontal: 20,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  headerTitle: { fontSize: 24, fontWeight: '900', color: '#1d1d1d' },
  content: { flex: 1 },
  listContent: { padding: 16 },
  downloadItem: {
    backgroundColor: 'white',
    padding: 16,
    borderRadius: 16,
    marginBottom: 12,
  },
  downloadHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  fileName: {
    fontSize: 14,
    fontWeight: '700',
    color: '#333',
    flex: 1,
    marginRight: 8,
  },
  statusTextItem: {
    fontSize: 10,
    color: '#666',
    textTransform: 'uppercase',
    fontWeight: '700',
  },
  progressBar: {
    height: 6,
    borderRadius: 3,
    backgroundColor: '#eee',
  },
  progressRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 8,
  },
  progressPercent: {
    fontSize: 10,
    color: '#999',
    fontWeight: '700',
  },
  cancelButton: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    backgroundColor: '#ffebee',
    borderRadius: 8,
  },
  cancelButtonText: {
    fontSize: 10,
    color: '#f44336',
    fontWeight: '700',
  },
  idleContainer: { alignItems: 'center', flex: 1, justifyContent: 'center', padding: 24 },
  idleIcon: { fontSize: 64, color: '#ddd', marginBottom: 16 },
  idleTitle: { fontSize: 20, fontWeight: '900', color: '#333', marginBottom: 8 },
  idleSubtitle: { fontSize: 14, color: '#888', textAlign: 'center', lineHeight: 20, maxWidth: 300 },
});

export default DownloadScreen;
