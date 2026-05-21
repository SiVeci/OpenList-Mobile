import React, { useState } from 'react';
import { View, Text, TouchableOpacity, TextInput, StyleSheet } from 'react-native';
import { Button } from 'react-native-paper';
import { FilterType } from '../types';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

interface Props {
  visible: boolean;
  filterType: FilterType;
  customExt: string;
  onFilterTypeChange: (type: FilterType) => void;
  onCustomExtChange: (ext: string) => void;
  onClose: () => void;
}

const FILTER_OPTIONS: { id: FilterType; icon: string; label: string }[] = [
  { id: 'all', icon: 'file-multiple', label: 'All Files' },
  { id: 'video', icon: 'file-video', label: 'Videos' },
  { id: 'image', icon: 'file-image', label: 'Images' },
  { id: 'doc', icon: 'file-document', label: 'Documents' },
  { id: 'others', icon: 'file-question', label: 'Others' },
  { id: 'custom', icon: 'tag', label: 'Custom Ext' },
];

const FilterSheet: React.FC<Props> = ({
  visible,
  filterType,
  customExt,
  onFilterTypeChange,
  onCustomExtChange,
  onClose,
}) => {
  const [localExt, setLocalExt] = useState(customExt);

  if (!visible) return null;

  return (
    <View style={styles.overlay}>
      <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={onClose} />
      <View style={styles.sheet}>
        <View style={styles.handle} />
        <Text style={styles.title}>Filter Types</Text>

        <View style={styles.filterGrid}>
          {FILTER_OPTIONS.map(f => (
            <TouchableOpacity
              key={f.id}
              style={[styles.filterButton, filterType === f.id && styles.filterButtonActive]}
              onPress={() => onFilterTypeChange(f.id)}
            >
              <Icon
                name={f.icon}
                size={16}
                color={filterType === f.id ? '#6750a4' : '#999'}
              />
              <Text style={[styles.filterText, filterType === f.id && styles.filterTextActive]}>
                {f.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {filterType === 'custom' && (
          <TextInput
            style={styles.customInput}
            placeholder="Extension (e.g. apk, iso)"
            value={localExt}
            onChangeText={v => {
              setLocalExt(v);
              onCustomExtChange(v);
            }}
            placeholderTextColor="#aaa"
          />
        )}

        <Button mode="contained" onPress={onClose} style={styles.applyButton} buttonColor="#6750a4">
          Apply Filter
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
  filterGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    marginBottom: 20,
  },
  filterButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    width: '47%',
    padding: 16,
    borderRadius: 16,
    backgroundColor: '#f5f5f5',
    borderWidth: 1,
    borderColor: '#eee',
  },
  filterButtonActive: {
    backgroundColor: '#ede7f6',
    borderColor: '#ce93d8',
  },
  filterText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#999',
  },
  filterTextActive: {
    color: '#6750a4',
  },
  customInput: {
    backgroundColor: '#f5f5f5',
    borderWidth: 1,
    borderColor: '#eee',
    borderRadius: 16,
    paddingHorizontal: 20,
    paddingVertical: 16,
    fontSize: 14,
    fontWeight: '700',
    color: '#333',
    marginBottom: 20,
  },
  applyButton: {
    borderRadius: 16,
    paddingVertical: 4,
  },
});

export default FilterSheet;
