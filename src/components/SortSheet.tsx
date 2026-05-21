import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Button } from 'react-native-paper';
import { SortKey, SortOrder } from '../types';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

interface Props {
  visible: boolean;
  sortKey: SortKey;
  sortOrder: SortOrder;
  foldersFirst: boolean;
  onSortKeyChange: (key: SortKey) => void;
  onSortOrderChange: (order: SortOrder) => void;
  onFoldersFirstChange: (value: boolean) => void;
  onClose: () => void;
}

const SortSheet: React.FC<Props> = ({
  visible,
  sortKey,
  sortOrder,
  foldersFirst,
  onSortKeyChange,
  onSortOrderChange,
  onFoldersFirstChange,
  onClose,
}) => {
  if (!visible) return null;

  return (
    <View style={styles.overlay}>
      <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={onClose} />
      <View style={styles.sheet}>
        <View style={styles.handle} />
        <Text style={styles.title}>Sort Files</Text>

        <View style={styles.sortKeyRow}>
          {(['name', 'modified', 'size'] as SortKey[]).map(k => (
            <TouchableOpacity
              key={k}
              style={[styles.sortKeyButton, sortKey === k && styles.sortKeyButtonActive]}
              onPress={() => onSortKeyChange(k)}
            >
              <Text style={[styles.sortKeyText, sortKey === k && styles.sortKeyTextActive]}>
                {k.charAt(0).toUpperCase() + k.slice(1)}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.orderRow}>
          <TouchableOpacity
            style={[styles.orderButton, sortOrder === 'asc' && styles.orderButtonActive]}
            onPress={() => onSortOrderChange('asc')}
          >
            <Icon name="arrow-up" size={16} color={sortOrder === 'asc' ? '#6750a4' : '#999'} />
            <Text style={[styles.orderText, sortOrder === 'asc' && styles.orderTextActive]}>Ascending</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.orderButton, sortOrder === 'desc' && styles.orderButtonActive]}
            onPress={() => onSortOrderChange('desc')}
          >
            <Icon name="arrow-down" size={16} color={sortOrder === 'desc' ? '#6750a4' : '#999'} />
            <Text style={[styles.orderText, sortOrder === 'desc' && styles.orderTextActive]}>Descending</Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity
          style={styles.foldersFirstRow}
          onPress={() => onFoldersFirstChange(!foldersFirst)}
        >
          <Icon name="file-tree" size={20} color={foldersFirst ? '#6750a4' : '#999'} />
          <Text style={styles.foldersFirstText}>Folders First</Text>
          <View style={[styles.toggle, foldersFirst && styles.toggleActive]}>
            <View style={[styles.toggleThumb, foldersFirst && styles.toggleThumbActive]} />
          </View>
        </TouchableOpacity>

        <Button mode="contained" onPress={onClose} style={styles.doneButton} buttonColor="#6750a4">
          Done
        </Button>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 60,
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
    borderTopLeftRadius: 40,
    borderTopRightRadius: 40,
    padding: 32,
    paddingBottom: 48,
  },
  handle: {
    width: 48,
    height: 6,
    backgroundColor: '#e0e0e0',
    borderRadius: 3,
    alignSelf: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: '900',
    color: '#1d1d1d',
    marginBottom: 20,
  },
  sortKeyRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 20,
  },
  sortKeyButton: {
    flex: 1,
    paddingVertical: 16,
    borderRadius: 16,
    backgroundColor: '#f5f5f5',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#eee',
  },
  sortKeyButtonActive: {
    backgroundColor: '#6750a4',
    borderColor: 'transparent',
  },
  sortKeyText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#999',
  },
  sortKeyTextActive: {
    color: 'white',
  },
  orderRow: {
    flexDirection: 'row',
    backgroundColor: '#f0f0f0',
    borderRadius: 24,
    padding: 6,
    gap: 6,
    marginBottom: 20,
  },
  orderButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 12,
    borderRadius: 20,
  },
  orderButtonActive: {
    backgroundColor: 'white',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  orderText: {
    fontSize: 12,
    fontWeight: '900',
    color: '#999',
  },
  orderTextActive: {
    color: '#6750a4',
  },
  foldersFirstRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
    borderRadius: 16,
    padding: 20,
    borderWidth: 1,
    borderColor: '#eee',
    marginBottom: 20,
  },
  foldersFirstText: {
    flex: 1,
    fontSize: 14,
    fontWeight: '700',
    color: '#555',
    marginLeft: 12,
  },
  toggle: {
    width: 48,
    height: 24,
    borderRadius: 12,
    backgroundColor: '#ccc',
    justifyContent: 'center',
    paddingLeft: 2,
  },
  toggleActive: {
    backgroundColor: '#6750a4',
  },
  toggleThumb: {
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: 'white',
  },
  toggleThumbActive: {
    marginLeft: 24,
  },
  doneButton: {
    borderRadius: 16,
    paddingVertical: 4,
  },
});

export default SortSheet;
