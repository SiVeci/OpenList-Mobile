import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  ScrollView,
  ActivityIndicator,
  TouchableOpacity,
  Linking,
  StyleSheet,
} from 'react-native';
import { AListFile, ServerConfig } from '../types';
import { Image } from 'react-native';

interface Props {
  file: AListFile;
  url: string;
  config: ServerConfig;
  type: 'image' | 'text' | 'pdf';
  onClose: () => void;
}

const PreviewModal: React.FC<Props> = ({ file, url, config, type, onClose }) => {
  const [textContent, setTextContent] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (type === 'image') {
      setLoading(false);
      return;
    }

    const loadContent = async () => {
      setLoading(true);
      setError(null);
      try {
        const headers: Record<string, string> = {
          'Authorization': config.token,
          'AList-Token': config.token,
        };
        const response = await fetch(url, { headers });
        if (!response.ok) {
          if (response.status === 403 || response.status === 401) {
            throw new Error('Access denied. Check server permissions.');
          }
          throw new Error(`Server returned ${response.status}.`);
        }

        if (type === 'text') {
          const text = await response.text();
          setTextContent(text);
        } else if (type === 'pdf') {
          Linking.openURL(url).catch(() => {
            throw new Error('Cannot open PDF.');
          });
        }
      } catch (err: any) {
        setError(err.message || 'Preview failed.');
      } finally {
        setLoading(false);
      }
    };

    loadContent();
  }, [url, type, config.token]);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={onClose} style={styles.closeButton}>
          <Text style={styles.closeButtonText}>✕</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>{file.name}</Text>
        <TouchableOpacity
          onPress={() => Linking.openURL(url)}
          style={styles.downloadButton}
        >
          <Text style={styles.downloadButtonText}>↓</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.content}>
        {loading ? (
          <View style={styles.loaderContainer}>
            <ActivityIndicator size="large" color="#6750a4" />
            <Text style={styles.loaderText}>Buffering Content...</Text>
          </View>
        ) : error ? (
          <View style={styles.errorContainer}>
            <Text style={styles.errorTitle}>Can't Load Preview</Text>
            <Text style={styles.errorMessage}>{error}</Text>
            <TouchableOpacity
              style={styles.errorButton}
              onPress={() => Linking.openURL(url)}
            >
              <Text style={styles.errorButtonText}>Open in System Browser</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.errorDismissButton} onPress={onClose}>
              <Text style={styles.errorDismissText}>Dismiss</Text>
            </TouchableOpacity>
          </View>
        ) : type === 'image' ? (
          <ScrollView
            style={styles.imageContainer}
            contentContainerStyle={styles.imageContent}
            maximumZoomScale={3}
            bouncesZoom
          >
            <Image
              source={{ uri: url, headers: { Authorization: config.token, 'AList-Token': config.token } }}
              style={styles.image}
              resizeMode="contain"
            />
          </ScrollView>
        ) : (
          <ScrollView style={styles.textContainer}>
            <Text style={styles.textContent}>{textContent}</Text>
          </ScrollView>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'white',
    zIndex: 60,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
    paddingTop: 48,
  },
  closeButton: {
    padding: 8,
  },
  closeButtonText: {
    fontSize: 18,
    color: '#666',
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginHorizontal: 16,
  },
  downloadButton: {
    padding: 8,
  },
  downloadButtonText: {
    fontSize: 22,
    color: '#6750a4',
    fontWeight: '700',
  },
  content: {
    flex: 1,
    backgroundColor: '#fafafa',
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
    fontWeight: '500',
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  errorTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#333',
    marginBottom: 8,
  },
  errorMessage: {
    fontSize: 14,
    color: '#888',
    textAlign: 'center',
    marginBottom: 24,
    lineHeight: 20,
  },
  errorButton: {
    backgroundColor: '#6750a4',
    paddingHorizontal: 32,
    paddingVertical: 16,
    borderRadius: 16,
    marginBottom: 12,
  },
  errorButtonText: {
    color: 'white',
    fontWeight: '700',
    fontSize: 14,
  },
  errorDismissButton: {
    paddingHorizontal: 32,
    paddingVertical: 16,
  },
  errorDismissText: {
    color: '#999',
    fontWeight: '600',
    fontSize: 14,
  },
  imageContainer: {
    flex: 1,
  },
  imageContent: {
    flexGrow: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  image: {
    width: '100%',
    height: 400,
  },
  textContainer: {
    flex: 1,
    padding: 16,
  },
  textContent: {
    fontFamily: 'monospace',
    fontSize: 13,
    color: '#555',
    lineHeight: 20,
  },
});

export default PreviewModal;
