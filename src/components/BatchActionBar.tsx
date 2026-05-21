import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
interface Props {
  selectedCount: number;
  hasFolderSelected: boolean;
  onBatchDownload: () => void;
  onBatchCopyLinks: () => void;
  onBatchDelete: () => void;
  onExitSelectionMode: () => void;
  batchActionLoading: boolean;
}

const BatchActionBar: React.FC<Props> = ({
  selectedCount,
  hasFolderSelected,
  onBatchDownload,
  onBatchCopyLinks,
  onBatchDelete,
  onExitSelectionMode,
  batchActionLoading,
}) => {
  return (
    <View style={styles.container}>
      <View style={styles.bar}>
        <View style={styles.leftSection}>
          <TouchableOpacity style={styles.exitButton} onPress={onExitSelectionMode}>
            <Text style={styles.exitIcon}>✕</Text>
          </TouchableOpacity>
          <View style={styles.countSection}>
            <Text style={styles.countNumber}>{selectedCount}</Text>
            <Text style={styles.countLabel}>SELECTED</Text>
          </View>
        </View>

        <View style={styles.actions}>
          <TouchableOpacity
            style={[styles.actionButton, (hasFolderSelected || batchActionLoading) && styles.actionButtonDisabled]}
            onPress={onBatchDownload}
            disabled={hasFolderSelected || batchActionLoading}
          >
            <Text style={styles.actionIcon}>↓</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.actionButton, (hasFolderSelected || batchActionLoading) && styles.actionButtonDisabled]}
            onPress={onBatchCopyLinks}
            disabled={hasFolderSelected || batchActionLoading}
          >
            <Text style={styles.actionIcon}>⊕</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.deleteAction}
            onPress={onBatchDelete}
            disabled={batchActionLoading}
          >
            <Text style={styles.deleteIcon}>🗑</Text>
          </TouchableOpacity>
        </View>
      </View>
      {hasFolderSelected && (
        <Text style={styles.folderWarning}>Batch download/link disabled for folders</Text>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    bottom: 32,
    left: 24,
    right: 24,
    zIndex: 50,
  },
  bar: {
    backgroundColor: '#1d1d1d',
    borderRadius: 36,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  leftSection: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    marginLeft: 4,
  },
  exitButton: {
    padding: 8,
  },
  exitIcon: {
    color: 'white',
    fontSize: 18,
    fontWeight: '700',
  },
  countSection: {
    flexDirection: 'column',
  },
  countNumber: {
    color: 'white',
    fontSize: 14,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  countLabel: {
    color: '#999',
    fontSize: 8,
    fontWeight: '900',
    letterSpacing: 3,
  },
  actions: {
    flexDirection: 'row',
    gap: 6,
  },
  actionButton: {
    backgroundColor: 'rgba(255,255,255,0.1)',
    padding: 14,
    borderRadius: 20,
  },
  actionButtonDisabled: {
    backgroundColor: '#333',
  },
  actionIcon: {
    color: 'white',
    fontSize: 20,
  },
  deleteAction: {
    backgroundColor: '#b3261e',
    padding: 14,
    borderRadius: 20,
  },
  deleteIcon: {
    fontSize: 18,
  },
  folderWarning: {
    textAlign: 'center',
    color: '#fb923c',
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 2,
    marginTop: 8,
    backgroundColor: 'rgba(0,0,0,0.5)',
    alignSelf: 'center',
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 10,
  },
});

export default BatchActionBar;
