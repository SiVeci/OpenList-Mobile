import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Checkbox } from 'react-native-paper';
import FileIcon from './FileIcon';
import { AListFile } from '../types';

interface Props {
  file: AListFile;
  isSelected: boolean;
  isSelectionMode: boolean;
  viewMode: 'list' | 'grid';
  onPress: () => void;
  onLongPress: () => void;
  onMorePress: () => void;
}

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

const FileItem: React.FC<Props> = ({
  file,
  isSelected,
  isSelectionMode,
  viewMode,
  onPress,
  onLongPress,
  onMorePress,
}) => {
  if (viewMode === 'grid') {
    return (
      <TouchableOpacity
        onPress={onPress}
        onLongPress={onLongPress}
        activeOpacity={0.7}
        style={[styles.gridItem, isSelected && styles.gridItemSelected]}
      >
        {isSelectionMode && (
          <View style={styles.gridCheckbox}>
            <Checkbox status={isSelected ? 'checked' : 'unchecked'} color="#6750a4" />
          </View>
        )}
        <View style={styles.gridIconContainer}>
          <FileIcon isDir={file.is_dir} name={file.name} size={48} />
        </View>
        <Text style={styles.gridName} numberOfLines={2}>{file.name}</Text>
        {!file.is_dir && (
          <Text style={styles.gridMeta}>{formatSize(file.size)}</Text>
        )}
      </TouchableOpacity>
    );
  }

  return (
    <TouchableOpacity
      onPress={onPress}
      onLongPress={onLongPress}
      activeOpacity={0.7}
      style={[styles.listItem, isSelected && styles.listItemSelected]}
    >
      {isSelectionMode && (
        <View style={styles.listCheckbox}>
          <Checkbox status={isSelected ? 'checked' : 'unchecked'} color="#6750a4" />
        </View>
      )}
      <View style={styles.listIconContainer}>
        <FileIcon isDir={file.is_dir} name={file.name} size={32} />
      </View>
      <View style={styles.listContent}>
        <Text style={styles.listName} numberOfLines={1}>{file.name}</Text>
        <Text style={styles.listMeta}>
          {file.is_dir ? 'Folder' : formatSize(file.size)} · {formatDate(file.modified)}
        </Text>
      </View>
      {!file.is_dir && !isSelectionMode && (
        <TouchableOpacity onPress={onMorePress} style={styles.moreButton}>
          <Text style={styles.moreButtonText}>⋮</Text>
        </TouchableOpacity>
      )}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  listItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'white',
    borderRadius: 24,
    padding: 14,
    marginBottom: 6,
    borderWidth: 1,
    borderColor: 'transparent',
  },
  listItemSelected: {
    backgroundColor: '#ede7f6',
    borderColor: '#ce93d8',
  },
  listCheckbox: {
    marginRight: 4,
  },
  listIconContainer: {
    marginRight: 16,
  },
  listContent: {
    flex: 1,
    minWidth: 0,
  },
  listName: {
    fontSize: 14,
    fontWeight: '700',
    color: '#333',
  },
  listMeta: {
    fontSize: 10,
    color: '#999',
    marginTop: 2,
    fontWeight: '500',
  },
  moreButton: {
    padding: 8,
  },
  moreButtonText: {
    fontSize: 20,
    color: '#ccc',
    fontWeight: '700',
  },
  gridItem: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: 'white',
    borderRadius: 24,
    padding: 14,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: 'transparent',
    position: 'relative',
  },
  gridItemSelected: {
    backgroundColor: '#ede7f6',
    borderColor: '#ce93d8',
  },
  gridCheckbox: {
    position: 'absolute',
    top: 4,
    left: 4,
    zIndex: 10,
  },
  gridIconContainer: {
    backgroundColor: '#f5f5f5',
    borderRadius: 16,
    padding: 16,
    marginBottom: 8,
    width: 80,
    height: 80,
    justifyContent: 'center',
    alignItems: 'center',
  },
  gridName: {
    fontSize: 11,
    fontWeight: '700',
    color: '#333',
    textAlign: 'center',
    lineHeight: 14,
  },
  gridMeta: {
    fontSize: 9,
    color: '#999',
    marginTop: 2,
  },
});

export default FileItem;
