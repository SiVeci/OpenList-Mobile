import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  Alert,
  Linking,
  StyleSheet,
} from 'react-native';
import { Button } from 'react-native-paper';
import Clipboard from '@react-native-clipboard/clipboard';
import { AListFile, ServerConfig, PlayerType } from '../types';
import { AListService } from '../services/alistService';
import FileIcon from './FileIcon';
import PreviewModal from './PreviewModal';
import { localFsService } from '../services/localFsService';
import { downloadService } from '../services/downloadService';
import AsyncStorage from '@react-native-async-storage/async-storage';

interface Props {
  file: AListFile;
  path: string;
  config: ServerConfig;
  onClose: () => void;
  onRefresh: () => void;
}

const VIDEO_EXTS = ['mp4', 'mkv', 'avi', 'mov', 'flv', 'wmv', 'rmvb'];
const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'];
const PDF_EXTS = ['pdf'];
const TEXT_EXTS = [
  'txt', 'md', 'json', 'yaml', 'yml', 'js', 'ts', 'py', 'css', 'html', 'conf',
  'ini', 'log', 'tsx', 'jsx', 'sh', 'sql', 'xml', 'csv', 'go', 'c', 'cpp', 'java',
];

const PLAYER_NAMES: Record<PlayerType, string> = {
  vlc: 'VLC',
  mx: 'MX Player',
  nplayer: 'nPlayer',
};

const ActionSheet: React.FC<Props> = ({ file, path, config, onClose, onRefresh }) => {
  const [rawUrl, setRawUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showPreview, setShowPreview] = useState(false);
  const [preferredPlayer, setPreferredPlayer] = useState<PlayerType>('nplayer');
  const [showPlayerPicker, setShowPlayerPicker] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const stored = await AsyncStorage.getItem('openlist_pref_player');
        if (stored) setPreferredPlayer(stored as PlayerType);
      } catch {}
    })();
  }, []);

  useEffect(() => {
    const fetchLink = async () => {
      try {
        const service = new AListService(config);
        const detail = await service.getFileDetail(path);
        setRawUrl(detail.data.raw_url);
      } catch (err: any) {
        setError('Failed to fetch direct link: ' + err.message);
      } finally {
        setLoading(false);
      }
    };
    fetchLink();
  }, [path, config]);

  const ext = file.name.split('.').pop()?.toLowerCase() || '';
  const isImage = IMAGE_EXTS.includes(ext);
  const isPdf = PDF_EXTS.includes(ext);
  const isText = TEXT_EXTS.includes(ext);
  const isVideo = VIDEO_EXTS.includes(ext);
  const canPreview = isImage || isText || isPdf;

  const handleCopyLink = () => {
    if (rawUrl) {
      Clipboard.setString(rawUrl);
      Alert.alert('Copied', 'Link copied to clipboard');
    }
  };

  const handleExternalPlayer = (player: PlayerType) => {
    if (!rawUrl) return;
    let url = '';
    switch (player) {
      case 'vlc':
        url = `vlc://${rawUrl}`;
        break;
      case 'mx':
        url = `intent:${rawUrl}#Intent;package=com.mxtech.videoplayer.ad;S.title=${encodeURIComponent(file.name)};end`;
        break;
      case 'nplayer':
        url = `nplayer-${rawUrl}`;
        break;
    }
    Linking.openURL(url).catch(() => {
      Alert.alert('Error', `Could not open ${PLAYER_NAMES[player]}. Is it installed?`);
    });
  };

  const selectPlayer = async (player: PlayerType) => {
    setPreferredPlayer(player);
    try {
      await AsyncStorage.setItem('openlist_pref_player', player);
    } catch {}
    setShowPlayerPicker(false);
  };

  const executeDelete = async () => {
    if (deleting) return;
    setDeleting(true);
    setError(null);
    try {
      const service = new AListService(config);
      const lastSlashIndex = path.lastIndexOf('/');
      const dir = path.substring(0, lastSlashIndex) || '/';
      const name = path.substring(lastSlashIndex + 1);
      await service.deleteFiles(dir, [name]);
      onRefresh();
      onClose();
    } catch (err: any) {
      setError('Delete failed: ' + err.message);
      setDeleting(false);
      setConfirmDelete(false);
    }
  };

  const handleSaveToDevice = async () => {
    try {
      const dirResult = await localFsService.pickDirectory();
      await downloadService.startDownload(
        config,
        path,
        dirResult.uri,
        file.size,
        'application/octet-stream' // generic mime, or better from extension
      );
      Alert.alert('Download Started', 'Check the Downloads tab for progress');
      onClose();
    } catch (err: any) {
      if (err.code !== 'CANCELLED') {
        setError('Save failed: ' + err.message);
      }
    }
  };

  return (
    <>
      <View style={styles.overlay}>
        <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={onClose} />
        <View style={styles.sheet}>
          <View style={styles.handle} />

          <View style={styles.header}>
            <View style={styles.headerIcon}>
              <FileIcon isDir={false} name={file.name} size={40} />
            </View>
            <View style={styles.headerInfo}>
              <Text style={styles.headerName} numberOfLines={1}>{file.name}</Text>
              <Text style={styles.headerExt}>{ext.toUpperCase()} File</Text>
            </View>
            <TouchableOpacity onPress={onClose} style={styles.closeButton}>
              <Text style={styles.closeButtonText}>X</Text>
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.body} showsVerticalScrollIndicator={false}>
            {loading ? (
              <ActivityIndicator size="large" color="#6750a4" style={styles.loader} />
            ) : confirmDelete ? (
              <View style={styles.deleteConfirm}>
                <Text style={styles.deleteConfirmTitle}>Confirm Deletion?</Text>
                <Text style={styles.deleteConfirmText}>
                  This action cannot be undone. Delete{' '}
                  <Text style={{ fontWeight: 'bold' }}>{file.name}</Text>?
                </Text>
                <View style={styles.deleteConfirmButtons}>
                  <Button
                    mode="outlined"
                    onPress={() => setConfirmDelete(false)}
                    disabled={deleting}
                    style={styles.deleteCancelButton}
                  >
                    Cancel
                  </Button>
                  <Button
                    mode="contained"
                    onPress={executeDelete}
                    loading={deleting}
                    disabled={deleting}
                    buttonColor="#b3261e"
                    style={styles.deleteConfirmButton}
                  >
                    Confirm
                  </Button>
                </View>
              </View>
            ) : (
              <View style={styles.actions}>
                {isVideo && (
                  <View style={styles.videoActions}>
                    <Button
                      mode="contained"
                      onPress={() => handleExternalPlayer(preferredPlayer)}
                      style={styles.playButton}
                      buttonColor="#6750a4"
                      icon="play"
                    >
                      Preview in {PLAYER_NAMES[preferredPlayer]}
                    </Button>
                    <TouchableOpacity
                      style={styles.playerSettingsButton}
                      onPress={() => setShowPlayerPicker(!showPlayerPicker)}
                    >
                      <Text style={styles.playerSettingsIcon}>⚙</Text>
                    </TouchableOpacity>
                  </View>
                )}

                {canPreview && (
                  <Button
                    mode="contained"
                    onPress={() => setShowPreview(true)}
                    style={styles.previewButton}
                    buttonColor="#6750a4"
                    icon="eye"
                  >
                    Preview Now
                  </Button>
                )}

                {showPlayerPicker && (
                  <View style={styles.playerPicker}>
                    <Text style={styles.playerPickerLabel}>PREFERRED VIDEO PLAYER</Text>
                    {(['vlc', 'mx', 'nplayer'] as PlayerType[]).map(p => (
                      <TouchableOpacity
                        key={p}
                        style={[styles.playerOption, preferredPlayer === p && styles.playerOptionActive]}
                        onPress={() => selectPlayer(p)}
                      >
                        <Text style={[styles.playerOptionText, preferredPlayer === p && styles.playerOptionTextActive]}>
                          {PLAYER_NAMES[p]}
                        </Text>
                        {preferredPlayer === p && <Text style={styles.playerCheck}>✓</Text>}
                      </TouchableOpacity>
                    ))}
                  </View>
                )}

                <View style={styles.secondaryActions}>
                  <TouchableOpacity
                    style={styles.secondaryButton}
                    onPress={handleSaveToDevice}
                  >
                    <Text style={styles.secondaryIcon}>💾</Text>
                    <Text style={styles.secondaryLabel}>Save to Device</Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    style={styles.secondaryButton}
                    onPress={() => {
                      if (rawUrl) Linking.openURL(rawUrl);
                    }}
                  >
                    <Text style={styles.secondaryIcon}>↓</Text>
                    <Text style={styles.secondaryLabel}>Browser Dl</Text>
                  </TouchableOpacity>

                  <TouchableOpacity style={styles.secondaryButton} onPress={handleCopyLink}>
                    <Text style={styles.secondaryIcon}>⊕</Text>
                    <Text style={styles.secondaryLabel}>Copy Link</Text>
                  </TouchableOpacity>
                </View>

                <TouchableOpacity
                  style={styles.deleteButton}
                  onPress={() => setConfirmDelete(true)}
                >
                  <Text style={styles.deleteIcon}>🗑</Text>
                  <Text style={styles.deleteLabel}>Delete File</Text>
                </TouchableOpacity>
              </View>
            )}

            {error && (
              <View style={styles.errorBox}>
                <Text style={styles.errorText}>{error}</Text>
              </View>
            )}
          </ScrollView>
        </View>
      </View>

      {showPreview && rawUrl && (
        <PreviewModal
          file={file}
          url={rawUrl}
          config={config}
          type={isImage ? 'image' : isPdf ? 'pdf' : 'text'}
          onClose={() => setShowPreview(false)}
        />
      )}
    </>
  );
};

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 50,
    justifyContent: 'flex-end',
  },
  backdrop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  sheet: {
    backgroundColor: 'white',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 24,
    padding: 24,
    maxHeight: '80%',
  },
  handle: {
    width: 48,
    height: 6,
    backgroundColor: '#e0e0e0',
    borderRadius: 3,
    alignSelf: 'center',
    marginBottom: 20,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 24,
  },
  headerIcon: {
    backgroundColor: '#f5f5f5',
    padding: 16,
    borderRadius: 16,
    marginRight: 16,
  },
  headerInfo: {
    flex: 1,
    minWidth: 0,
  },
  headerName: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1d1d1d',
  },
  headerExt: {
    fontSize: 10,
    color: '#aaa',
    marginTop: 2,
    fontWeight: '600',
    letterSpacing: 2,
    textTransform: 'uppercase',
  },
  closeButton: {
    padding: 8,
  },
  closeButtonText: {
    fontSize: 16,
    color: '#aaa',
    fontWeight: '700',
  },
  body: {
    maxHeight: 400,
  },
  loader: {
    paddingVertical: 40,
  },
  actions: {
    gap: 12,
  },
  videoActions: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
  },
  playButton: {
    flex: 1,
    borderRadius: 16,
    paddingVertical: 4,
  },
  playerSettingsButton: {
    padding: 16,
    backgroundColor: '#f5f5f5',
    borderRadius: 16,
  },
  playerSettingsIcon: {
    fontSize: 20,
  },
  previewButton: {
    borderRadius: 16,
    paddingVertical: 4,
  },
  playerPicker: {
    backgroundColor: '#f5f5f5',
    borderRadius: 16,
    padding: 8,
  },
  playerPickerLabel: {
    fontSize: 10,
    fontWeight: '900',
    color: '#ccc',
    letterSpacing: 2,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  playerOption: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 12,
    borderRadius: 12,
  },
  playerOptionActive: {
    backgroundColor: 'white',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 1,
  },
  playerOptionText: {
    fontSize: 14,
    fontWeight: '700',
    color: '#999',
  },
  playerOptionTextActive: {
    color: '#6750a4',
  },
  playerCheck: {
    fontSize: 14,
    color: '#6750a4',
    fontWeight: '700',
  },
  secondaryActions: {
    flexDirection: 'row',
    gap: 12,
  },
  secondaryButton: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f5f5f5',
    borderRadius: 16,
    padding: 16,
    gap: 8,
  },
  secondaryIcon: {
    fontSize: 24,
    color: '#555',
  },
  secondaryLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#555',
  },
  deleteButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fce4ec',
    borderRadius: 16,
    padding: 16,
    gap: 8,
  },
  deleteIcon: {
    fontSize: 18,
  },
  deleteLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#b3261e',
  },
  deleteConfirm: {
    backgroundColor: '#fce4ec',
    borderRadius: 16,
    padding: 24,
    borderWidth: 1,
    borderColor: '#ffcdd2',
  },
  deleteConfirmTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#b3261e',
    marginBottom: 8,
  },
  deleteConfirmText: {
    fontSize: 14,
    color: '#c62828',
    marginBottom: 20,
    lineHeight: 20,
  },
  deleteConfirmButtons: {
    flexDirection: 'row',
    gap: 12,
  },
  deleteCancelButton: {
    flex: 1,
    borderRadius: 12,
  },
  deleteConfirmButton: {
    flex: 1,
    borderRadius: 12,
    paddingVertical: 2,
  },
  errorBox: {
    backgroundColor: '#fce4ec',
    borderRadius: 12,
    padding: 12,
    marginTop: 12,
    borderWidth: 1,
    borderColor: '#ffcdd2',
  },
  errorText: {
    fontSize: 12,
    color: '#c62828',
    textAlign: 'center',
    fontWeight: '500',
  },
});

export default ActionSheet;
