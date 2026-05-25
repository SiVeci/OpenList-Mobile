import React, { useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  Switch,
  ActivityIndicator,
  StyleSheet,
  Alert,
} from 'react-native';
import { Button, Dialog, Portal, TextInput, SegmentedButtons, RadioButton } from 'react-native-paper';
import { useAppStore } from '../stores/appStore';
import { useAuthStore } from '../stores/authStore';
import { syncService, SyncRule } from '../services/syncService';
import { localFsService } from '../services/localFsService';
import RemoteDirPicker from '../components/RemoteDirPicker';

const SyncScreen = () => {
  const config = useAuthStore(state => state.config);
  const { syncRules, addSyncRule, updateSyncRule, deleteSyncRule, setLastSyncTime } = useAppStore();
  const [syncing, setSyncing] = useState<string | null>(null);
  const [syncProgress, setSyncProgress] = useState({ current: 0, total: 0 });
  const [showAddDialog, setShowAddDialog] = useState(false);

  const [newRuleName, setNewRuleName] = useState('');
  const [newRuleRemotePath, setNewRuleRemotePath] = useState('/');
  const [newRuleLocalUri, setNewRuleLocalUri] = useState('');
  const [newRuleLocalName, setNewRuleLocalName] = useState('');
  const [newRuleDirection, setNewRuleDirection] = useState<'remote_to_local' | 'local_to_remote' | 'bidirectional'>('remote_to_local');
  const [newRuleConflict, setNewRuleConflict] = useState<'skip' | 'newer_wins' | 'manual'>('newer_wins');
  const [newRuleInterval, setNewRuleInterval] = useState('60');
  const [newRuleWifiOnly, setNewRuleWifiOnly] = useState(true);
  const [newRuleChargingOnly, setNewRuleChargingOnly] = useState(false);
  const [isPickerVisible, setIsPickerVisible] = useState(false);

  const pickLocalDir = async () => {
    try {
      const result = await localFsService.pickDirectory();
      setNewRuleLocalUri(result.uri);
      setNewRuleLocalName(result.name);
    } catch {}
  };

  const handleAddRule = () => {
    if (!newRuleName.trim() || !newRuleLocalUri) {
      Alert.alert('Error', 'Please fill in all required fields and select a local directory.');
      return;
    }
    const rule: SyncRule = {
      id: Date.now().toString(),
      name: newRuleName.trim(),
      remotePath: newRuleRemotePath,
      localDirUri: newRuleLocalUri,
      localDirName: newRuleLocalName,
      direction: newRuleDirection,
      conflictStrategy: newRuleConflict,
      enabled: true,
      lastSyncTime: null,
      intervalMinutes: parseInt(newRuleInterval, 10) || 60,
      wifiOnly: newRuleWifiOnly,
      chargingOnly: newRuleChargingOnly,
    };
    addSyncRule(rule);
    resetNewRuleForm();
    setShowAddDialog(false);
  };

  const resetNewRuleForm = () => {
    setNewRuleName('');
    setNewRuleRemotePath('/');
    setNewRuleLocalUri('');
    setNewRuleLocalName('');
    setNewRuleDirection('remote_to_local');
    setNewRuleConflict('newer_wins');
    setNewRuleInterval('60');
    setNewRuleWifiOnly(true);
    setNewRuleChargingOnly(false);
  };

  const handleSync = async (rule: SyncRule) => {
    if (!config) return;
    setSyncing(rule.id);
    setSyncProgress({ current: 0, total: 0 });
    try {
      const result = await syncService.executeSync(
        config,
        rule,
        (current, total) => setSyncProgress({ current, total }),
      );
      setLastSyncTime(rule.id, Date.now());

      const msg = `Synced: ${result.synced}\nSkipped: ${result.skipped}\nErrors: ${result.errors.length}`;
      Alert.alert('Sync Complete', msg);
    } catch (err: any) {
      Alert.alert('Sync Error', err.message);
    } finally {
      setSyncing(null);
    }
  };

  const formatDate = (timestamp: number | null) => {
    if (!timestamp) return 'Never';
    return new Date(timestamp).toLocaleString();
  };

  const renderRule = ({ item }: { item: SyncRule }) => (
    <View style={styles.ruleCard}>
      <View style={styles.ruleHeader}>
        <View style={styles.ruleHeaderLeft}>
          <Text style={styles.ruleName}>{item.name}</Text>
          <Text style={styles.rulePath}>{item.remotePath} ↔ {item.localDirName}</Text>
        </View>
        <Switch
          value={item.enabled}
          onValueChange={v => updateSyncRule(item.id, { enabled: v })}
          trackColor={{ false: '#ddd', true: '#6750a4' }}
        />
      </View>

      <View style={styles.ruleMeta}>
        <Text style={styles.ruleMetaText}>Direction: {item.direction.replace(/_/g, ' ')}</Text>
        <Text style={styles.ruleMetaText}>Interval: {item.intervalMinutes}min</Text>
        <Text style={styles.ruleMetaText}>Last sync: {formatDate(item.lastSyncTime)}</Text>
      </View>

      {syncing === item.id && (
        <View style={styles.syncProgress}>
          <ActivityIndicator size="small" color="#6750a4" />
          <Text style={styles.syncProgressText}>
            {syncProgress.current}/{syncProgress.total} files
          </Text>
        </View>
      )}

      <View style={styles.ruleActions}>
        <Button
          mode="contained"
          onPress={() => handleSync(item)}
          disabled={syncing !== null || !item.enabled}
          buttonColor="#6750a4"
          style={styles.syncButton}
          labelStyle={styles.syncButtonLabel}
        >
          Sync Now
        </Button>
        <TouchableOpacity
          onPress={() => Alert.alert(
            'Delete Rule',
            `Remove "${item.name}"?`,
            [
              { text: 'Cancel', style: 'cancel' },
              { text: 'Delete', style: 'destructive', onPress: () => deleteSyncRule(item.id) },
            ]
          )}
          style={styles.deleteRuleButton}
        >
          <Text style={styles.deleteRuleText}>Delete</Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Auto Sync</Text>
        <Button
          mode="contained"
          onPress={() => setShowAddDialog(true)}
          buttonColor="#6750a4"
          compact
        >
          Add Rule
        </Button>
      </View>

      <FlatList
        data={syncRules}
        renderItem={renderRule}
        keyExtractor={item => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyIcon}>🔄</Text>
            <Text style={styles.emptyTitle}>No Sync Rules</Text>
            <Text style={styles.emptySubtitle}>
              Create rules to automatically sync files between your AList server and local device
            </Text>
          </View>
        }
      />

      <Portal>
        <Dialog visible={showAddDialog} onDismiss={() => { setShowAddDialog(false); resetNewRuleForm(); }}>
          <Dialog.Title>New Sync Rule</Dialog.Title>
          <Dialog.Content>
            <TextInput
              label="Rule Name"
              value={newRuleName}
              onChangeText={setNewRuleName}
              mode="outlined"
              style={styles.dialogInput}
            />
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 12 }}>
              <TextInput
                label="Remote Path"
                value={newRuleRemotePath}
                onChangeText={setNewRuleRemotePath}
                mode="outlined"
                style={{ flex: 1 }}
              />
              <Button 
                mode="contained-tonal" 
                onPress={() => setIsPickerVisible(true)}
                style={{ marginTop: 6 }}
              >
                Browse
              </Button>
            </View>
            <Button
              mode="outlined"
              onPress={pickLocalDir}
              style={styles.dialogButton}
              icon="folder"
            >
              {newRuleLocalName ? `Local: ${newRuleLocalName}` : 'Select Local Directory'}
            </Button>

            <Text style={styles.dialogLabel}>Direction</Text>
            <SegmentedButtons
              value={newRuleDirection}
              onValueChange={v => setNewRuleDirection(v as any)}
              buttons={[
                { value: 'remote_to_local', label: '→ Local' },
                { value: 'local_to_remote', label: '→ Remote' },
                { value: 'bidirectional', label: '↔ Both' },
              ]}
            />

            <Text style={styles.dialogLabel}>Conflict Strategy</Text>
            <RadioButton.Group
              value={newRuleConflict}
              onValueChange={v => setNewRuleConflict(v as any)}
            >
              <RadioButton.Item label="Skip" value="skip" />
              <RadioButton.Item label="Newer wins" value="newer_wins" />
              <RadioButton.Item label="Manual" value="manual" />
            </RadioButton.Group>

            <TextInput
              label="Interval (minutes)"
              value={newRuleInterval}
              onChangeText={setNewRuleInterval}
              keyboardType="number-pad"
              mode="outlined"
              style={styles.dialogInput}
            />

            <View style={styles.dialogSwitchRow}>
              <Text>WiFi only</Text>
              <Switch value={newRuleWifiOnly} onValueChange={setNewRuleWifiOnly} />
            </View>
            <View style={styles.dialogSwitchRow}>
              <Text>Charging only</Text>
              <Switch value={newRuleChargingOnly} onValueChange={setNewRuleChargingOnly} />
            </View>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => { setShowAddDialog(false); resetNewRuleForm(); }}>Cancel</Button>
            <Button onPress={handleAddRule}>Create</Button>
          </Dialog.Actions>
        </Dialog>

        <RemoteDirPicker 
          visible={isPickerVisible} 
          onDismiss={() => setIsPickerVisible(false)}
          onSelect={(p) => {
            setNewRuleRemotePath(p);
            setIsPickerVisible(false);
          }}
        />
      </Portal>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f7f2fa' },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: 'white',
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
    paddingTop: 48,
    paddingBottom: 16,
    paddingHorizontal: 20,
  },
  headerTitle: { fontSize: 24, fontWeight: '900', color: '#1d1d1d' },
  listContent: { paddingHorizontal: 16, paddingTop: 16, paddingBottom: 32 },
  ruleCard: {
    backgroundColor: 'white',
    borderRadius: 24,
    padding: 20,
    marginBottom: 12,
  },
  ruleHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  ruleHeaderLeft: { flex: 1, minWidth: 0 },
  ruleName: { fontSize: 16, fontWeight: '700', color: '#1d1d1d' },
  rulePath: { fontSize: 11, color: '#888', marginTop: 2 },
  ruleMeta: { marginBottom: 12 },
  ruleMetaText: { fontSize: 11, color: '#999', marginBottom: 2 },
  syncProgress: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 12 },
  syncProgressText: { fontSize: 12, color: '#6750a4', fontWeight: '600' },
  ruleActions: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  syncButton: { borderRadius: 12 },
  syncButtonLabel: { fontSize: 12 },
  deleteRuleButton: { padding: 8 },
  deleteRuleText: { color: '#b3261e', fontWeight: '600', fontSize: 12 },
  emptyContainer: { alignItems: 'center', paddingVertical: 60 },
  emptyIcon: { fontSize: 48, marginBottom: 16 },
  emptyTitle: { fontSize: 18, fontWeight: '900', color: '#333', marginBottom: 8 },
  emptySubtitle: { fontSize: 13, color: '#888', textAlign: 'center', lineHeight: 20, maxWidth: 260 },
  dialogInput: { marginBottom: 12 },
  dialogButton: { marginBottom: 12 },
  dialogLabel: { fontSize: 12, fontWeight: '700', color: '#666', marginTop: 8, marginBottom: 4 },
  dialogSwitchRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginVertical: 4 },
});

export default SyncScreen;
