import React, { useState, useEffect } from 'react';
import { Modal, FlatList, TouchableOpacity, Text, ActivityIndicator, StyleSheet, View } from 'react-native';
import { Button, Appbar } from 'react-native-paper';
import { AListService } from '../services/alistService';
import { AListFile } from '../types';
import { useAuthStore } from '../stores/authStore';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

interface Props {
  visible: boolean;
  onDismiss: () => void;
  onSelect: (path: string) => void;
}

export default function RemoteDirPicker({ visible, onDismiss, onSelect }: Props) {
  const config = useAuthStore(state => state.config);
  const [path, setPath] = useState('/');
  const [files, setFiles] = useState<AListFile[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (visible && config) {
      loadDir(path);
    }
  }, [visible, path, config]);

  const loadDir = async (p: string) => {
    if (!config) return;
    setLoading(true);
    try {
      const service = new AListService(config);
      const res = await service.listFiles(p, 1, 1000, false);
      setFiles((res.data.content || []).filter(f => f.is_dir));
    } catch (e) {
      console.error('Picker error:', e);
    } finally {
      setLoading(false);
    }
  };

  const goBack = () => {
    if (path === '/') return;
    const parts = path.split('/').filter(Boolean);
    parts.pop();
    setPath('/' + parts.join('/'));
  };

  return (
    <Modal visible={visible} onRequestClose={onDismiss} animationType='slide'>
      <Appbar.Header style={{ backgroundColor: 'white' }}>
        <Appbar.BackAction onPress={onDismiss} />
        <Appbar.Content title='Select Directory' titleStyle={{ fontSize: 18 }} />
      </Appbar.Header>

      <View style={styles.breadcrumbContainer}>
        <Text style={styles.breadcrumbText} numberOfLines={1}>Current: {path}</Text>
      </View>
      
      {path !== '/' && (
        <TouchableOpacity style={styles.item} onPress={goBack}>
          <Icon name='folder-upload' size={24} color='#6750a4' />
          <Text style={styles.itemText}>..</Text>
        </TouchableOpacity>
      )}

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator size='large' color='#6750a4' />
        </View>
      ) : (
        <FlatList
          data={files}
          keyExtractor={item => item.name}
          renderItem={({ item }) => (
            <TouchableOpacity 
              style={styles.item} 
              onPress={() => setPath((path === '/' ? '' : path) + '/' + item.name)}
            >
              <Icon name='folder' size={24} color='#6750a4' />
              <Text style={styles.itemText}>{item.name}</Text>
            </TouchableOpacity>
          )}
          ListEmptyComponent={
            !loading ? (
              <View style={styles.center}>
                <Text style={styles.emptyText}>No subdirectories</Text>
              </View>
            ) : null
          }
        />
      )}
      
      <View style={styles.footer}>
        <Button 
          mode='contained' 
          onPress={() => onSelect(path)} 
          style={styles.selectBtn}
          labelStyle={{ fontWeight: 'bold' }}
        >
          Upload to this folder
        </Button>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 40,
  },
  breadcrumbContainer: {
    padding: 12,
    backgroundColor: '#f8f9fa',
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  breadcrumbText: {
    fontSize: 13,
    color: '#666',
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0'
  },
  itemText: {
    marginLeft: 16,
    fontSize: 16,
    color: '#333'
  },
  emptyText: {
    color: '#999',
    fontSize: 14,
  },
  footer: {
    padding: 16,
    backgroundColor: 'white',
    borderTopWidth: 1,
    borderTopColor: '#eee',
  },
  selectBtn: {
    backgroundColor: '#6750a4',
    borderRadius: 8,
  }
});