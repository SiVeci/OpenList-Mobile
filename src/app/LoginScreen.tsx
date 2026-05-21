import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  Alert,
  StyleSheet,
} from 'react-native';
import { Button, SegmentedButtons, Dialog, Portal } from 'react-native-paper';
import { useAuthStore } from '../stores/authStore';

const LoginScreen = () => {
  const { login, savedConfigs, deleteSavedConfig, isLoading, error } = useAuthStore();

  const [serverName, setServerName] = useState('');
  const [protocol, setProtocol] = useState<'http://' | 'https://'>('http://');
  const [address, setAddress] = useState('');
  const [port, setPort] = useState('5244');
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [showHistory, setShowHistory] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<{ index: number; name: string } | null>(null);

  const checkIsLocal = (addr: string) => {
    const lower = addr.toLowerCase().trim();
    if (!lower) return false;
    if (lower === 'localhost' || lower === '127.0.0.1' || lower.endsWith('.local')) return true;
    if (/^192\.168\./.test(lower)) return true;
    if (/^10\./.test(lower)) return true;
    const parts = lower.split('.');
    if (parts.length === 4 && parts[0] === '172') {
      const second = parseInt(parts[1], 10);
      if (second >= 16 && second <= 31) return true;
    }
    return false;
  };

  const isLocal = checkIsLocal(address);
  const isMixedContent = protocol === 'http://' && !isLocal;

  const handleAddressChange = (val: string) => {
    let clean = val.trim();
    if (clean.toLowerCase().startsWith('https://')) {
      setProtocol('https://');
      clean = clean.substring(8);
    } else if (clean.toLowerCase().startsWith('http://')) {
      setProtocol('http://');
      clean = clean.substring(7);
    }
    if (clean.includes(':')) {
      const parts = clean.split(':');
      clean = parts[0];
      const possiblePort = parts[1].split('/')[0];
      if (possiblePort && !isNaN(parseInt(possiblePort, 10))) {
        setPort(possiblePort);
      }
    }
    setAddress(clean);
  };

  const selectSavedConfig = (cfg: any) => {
    setServerName(cfg.serverName);
    setUsername(cfg.username);
    try {
      const url = new URL(cfg.url);
      setProtocol(url.protocol === 'https:' ? 'https://' : 'http://');
      setAddress(url.hostname);
      setPort(url.port || (url.protocol === 'https:' ? '443' : '80'));
    } catch {
      const isHttps = cfg.url.startsWith('https://');
      setProtocol(isHttps ? 'https://' : 'http://');
      const withoutProto = cfg.url.replace(/^https?:\/\//, '');
      const parts = withoutProto.split(':');
      setAddress(parts[0]);
      if (parts[1]) setPort(parts[1]);
    }
    setShowHistory(false);
  };

  const handleLogin = async () => {
    if (!address.trim()) {
      Alert.alert('Error', 'Please enter a server address.');
      return;
    }
    const cleanAddress = address.trim().replace(/\/$/, '');
    const cleanPort = port.trim();
    const targetUrl = `${protocol}${cleanAddress}${cleanPort ? `:${cleanPort}` : ''}`;
    const finalServerName = serverName.trim() || 'Server';

    try {
      await login(targetUrl, username, password, finalServerName);
    } catch {}
  };

  const confirmDeleteSaved = () => {
    if (deleteTarget === null) return;
    deleteSavedConfig(deleteTarget.index);
    setDeleteTarget(null);
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={styles.container}
    >
      <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
        <View style={styles.logoContainer}>
          <View style={styles.logoIcon}>
            <Text style={styles.logoIconText}>AL</Text>
          </View>
          <Text style={styles.title}>Remote Storage Manager</Text>
          <Text style={styles.subtitle}>SUPPORT ALIST AND OPENLIST SERVERS</Text>
        </View>

        {savedConfigs.length > 0 && (
          <View style={styles.section}>
            <View style={styles.sectionHeader}>
              <Text style={styles.sectionLabel}>HISTORY</Text>
            </View>
            <TouchableOpacity
              style={styles.historyButton}
              onPress={() => setShowHistory(!showHistory)}
            >
              <Text style={styles.historyButtonText}>
                {serverName ? `Current: ${serverName}` : 'Pick a saved server...'}
              </Text>
            </TouchableOpacity>
            {showHistory && (
              <View style={styles.historyList}>
                {savedConfigs.map((cfg, idx) => (
                  <View key={idx} style={styles.historyItem}>
                    <TouchableOpacity
                      style={styles.historyItemContent}
                      onPress={() => selectSavedConfig(cfg)}
                    >
                      <Text style={styles.historyItemName}>{cfg.serverName}</Text>
                      <Text style={styles.historyItemUrl}>{cfg.url}</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={styles.historyItemDelete}
                      onPress={() => setDeleteTarget({ index: idx, name: cfg.serverName })}
                    >
                      <Text style={styles.historyItemDeleteText}>X</Text>
                    </TouchableOpacity>
                  </View>
                ))}
              </View>
            )}
          </View>
        )}

        <View style={styles.section}>
          <Text style={styles.sectionLabel}>GENERAL INFO</Text>
          <TextInput
            style={styles.input}
            placeholder="Server Nickname"
            value={serverName}
            onChangeText={setServerName}
            placeholderTextColor="#aaa"
          />
        </View>

        <View style={styles.section}>
          <View style={styles.sectionHeaderRow}>
            <Text style={styles.sectionLabel}>SERVER CONFIG</Text>
            {isLocal ? (
              <View style={[styles.badge, styles.badgeLocal]}>
                <Text style={styles.badgeTextLocal}>Local</Text>
              </View>
            ) : protocol === 'https://' ? (
              <View style={[styles.badge, styles.badgeSSL]}>
                <Text style={styles.badgeTextSSL}>SSL</Text>
              </View>
            ) : (
              <View style={[styles.badge, styles.badgeNoSSL]}>
                <Text style={styles.badgeTextNoSSL}>No SSL</Text>
              </View>
            )}
          </View>
          <View style={styles.serverConfigRow}>
            <SegmentedButtons
              value={protocol}
              onValueChange={v => setProtocol(v as 'http://' | 'https://')}
              buttons={[
                { value: 'http://', label: 'HTTP' },
                { value: 'https://', label: 'HTTPS' },
              ]}
              style={styles.protocolSegment}
            />
            <TextInput
              style={styles.portInput}
              placeholder="Port"
              value={port}
              onChangeText={setPort}
              keyboardType="number-pad"
              placeholderTextColor="#aaa"
            />
          </View>
          <TextInput
            style={styles.input}
            placeholder="Address (IP or Domain)"
            value={address}
            onChangeText={handleAddressChange}
            placeholderTextColor="#aaa"
          />
        </View>

        <View style={styles.section}>
          <TextInput
            style={styles.input}
            placeholder="Username"
            value={username}
            onChangeText={setUsername}
            autoCapitalize="none"
            placeholderTextColor="#aaa"
          />
          <View style={styles.spacer} />
          <TextInput
            style={styles.input}
            placeholder="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            placeholderTextColor="#aaa"
          />
        </View>

        {isMixedContent && (
          <View style={styles.mixedContentWarning}>
            <Text style={styles.mixedContentText}>
              Mixed Content: HTTPS apps cannot connect to external HTTP servers.
            </Text>
          </View>
        )}

        {error && (
          <View style={styles.errorBox}>
            <Text style={styles.errorText}>{error}</Text>
          </View>
        )}

        <Button
          mode="contained"
          onPress={handleLogin}
          loading={isLoading}
          disabled={isLoading}
          style={styles.loginButton}
          labelStyle={styles.loginButtonLabel}
          buttonColor="#6750a4"
        >
          Connect Now
        </Button>

        <Text style={styles.version}>V0.1.3</Text>
      </ScrollView>

      <Portal>
        <Dialog visible={deleteTarget !== null} onDismiss={() => setDeleteTarget(null)}>
          <Dialog.Title>Delete Saved Config?</Dialog.Title>
          <Dialog.Content>
            <Text>
              Are you sure you want to remove{' '}
              <Text style={{ fontWeight: 'bold' }}>{deleteTarget?.name}</Text> from your history?
            </Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setDeleteTarget(null)}>Cancel</Button>
            <Button onPress={confirmDeleteSaved} textColor="#b3261e">Delete</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f7f2fa',
  },
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: 24,
  },
  logoContainer: {
    alignItems: 'center',
    marginBottom: 28,
  },
  logoIcon: {
    width: 56,
    height: 56,
    borderRadius: 16,
    backgroundColor: '#6750a4',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
    shadowColor: '#6750a4',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 8,
  },
  logoIconText: {
    color: 'white',
    fontWeight: '900',
    fontSize: 20,
  },
  title: {
    fontSize: 18,
    fontWeight: '900',
    color: '#1d1d1d',
    letterSpacing: -0.5,
  },
  subtitle: {
    fontSize: 9,
    color: '#999',
    fontWeight: '900',
    marginTop: 4,
    letterSpacing: 2,
  },
  section: {
    marginBottom: 16,
  },
  sectionHeader: {
    marginBottom: 6,
  },
  sectionHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  sectionLabel: {
    fontSize: 9,
    fontWeight: '900',
    color: '#999',
    letterSpacing: 2,
    paddingLeft: 4,
    marginBottom: 6,
  },
  input: {
    backgroundColor: '#f5f5f5',
    borderWidth: 1,
    borderColor: '#eee',
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 14,
    fontWeight: '500',
    color: '#333',
  },
  serverConfigRow: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
    marginBottom: 8,
  },
  protocolSegment: {
    width: 140,
  },
  portInput: {
    flex: 1,
    backgroundColor: 'white',
    borderWidth: 1,
    borderColor: '#eee',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    fontSize: 12,
    fontFamily: 'monospace',
    color: '#333',
  },
  spacer: {
    height: 8,
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  badgeLocal: { backgroundColor: '#e3f2fd' },
  badgeSSL: { backgroundColor: '#e8f5e9' },
  badgeNoSSL: { backgroundColor: '#fff3e0' },
  badgeTextLocal: { fontSize: 8, fontWeight: '900', color: '#1565c0' },
  badgeTextSSL: { fontSize: 8, fontWeight: '900', color: '#2e7d32' },
  badgeTextNoSSL: { fontSize: 8, fontWeight: '900', color: '#e65100' },
  mixedContentWarning: {
    backgroundColor: '#fff3e0',
    borderWidth: 1,
    borderColor: '#ffe0b2',
    borderRadius: 12,
    padding: 10,
    marginBottom: 12,
  },
  mixedContentText: {
    fontSize: 11,
    color: '#e65100',
    fontWeight: '600',
  },
  errorBox: {
    backgroundColor: '#fce4ec',
    borderWidth: 1,
    borderColor: '#ffcdd2',
    borderRadius: 12,
    padding: 10,
    marginBottom: 12,
  },
  errorText: {
    fontSize: 11,
    color: '#c62828',
    fontWeight: '600',
  },
  loginButton: {
    borderRadius: 16,
    paddingVertical: 4,
    marginTop: 4,
  },
  loginButtonLabel: {
    fontSize: 15,
    fontWeight: '700',
  },
  version: {
    textAlign: 'center',
    fontSize: 9,
    color: '#ccc',
    fontWeight: '900',
    letterSpacing: 3,
    marginTop: 20,
  },
  historyButton: {
    backgroundColor: '#f5f5f5',
    borderWidth: 1,
    borderColor: '#eee',
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  historyButtonText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#666',
  },
  historyList: {
    backgroundColor: 'white',
    borderWidth: 1,
    borderColor: '#eee',
    borderRadius: 16,
    marginTop: 8,
    overflow: 'hidden',
  },
  historyItem: {
    flexDirection: 'row',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: '#f5f5f5',
  },
  historyItemContent: {
    flex: 1,
    padding: 12,
  },
  historyItemName: {
    fontSize: 12,
    fontWeight: '700',
    color: '#333',
  },
  historyItemUrl: {
    fontSize: 9,
    color: '#aaa',
    marginTop: 2,
  },
  historyItemDelete: {
    padding: 12,
  },
  historyItemDeleteText: {
    color: '#ef5350',
    fontWeight: '700',
  },
});

export default LoginScreen;
