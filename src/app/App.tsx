import React from 'react';
import { StatusBar } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { PaperProvider, MD3LightTheme as defaultTheme, Dialog, Portal, TextInput, Button } from 'react-native-paper';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { useAuthStore } from '../stores/authStore';
import { getPendingShare, onShareReceived, ShareEventData } from '../native/NativeShareReceiver';
import { AListService } from '../services/alistService';
import { localFsService } from '../services/localFsService';
import { Alert } from 'react-native';
import LoginScreen from './LoginScreen';
import FileBrowserScreen from './FileBrowserScreen';
import LocalBrowserScreen from './LocalBrowserScreen';
import DownloadScreen from './DownloadScreen';
import SyncScreen from './SyncScreen';
import { useAppStore } from '../stores/appStore';
import { syncService } from '../services/syncService';

export type RootStackParamList = {
  Login: undefined;
  Main: undefined;
};

export type MainTabParamList = {
  Remote: undefined;
  Local: undefined;
  Downloads: undefined;
  Sync: undefined;
};

const Stack = createStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator<MainTabParamList>();

const theme = {
  ...defaultTheme,
  colors: {
    ...defaultTheme.colors,
    primary: '#6750a4',
    primaryContainer: '#e8def8',
    secondary: '#625b71',
    secondaryContainer: '#e8def8',
    surface: '#ffffff',
    surfaceVariant: '#f7f2fa',
    background: '#f7f2fa',
    error: '#b3261e',
  },
};

const TAB_ICON_MAP: Record<string, string> = {
  Remote: 'cloud-outline',
  Local: 'folder-outline',
  Downloads: 'download-outline',
  Sync: 'sync',
};

function TabIcon({ route, color, size }: { route: string; color: string; size: number }) {
  const iconName = TAB_ICON_MAP[route] || 'help-circle-outline';
  return <Icon name={iconName} size={size} color={color} />;
}

function MainTabs() {
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: '#6750a4',
        tabBarInactiveTintColor: '#999',
        tabBarStyle: {
          backgroundColor: 'white',
          borderTopWidth: 1,
          borderTopColor: '#f0f0f0',
          paddingBottom: 4,
          height: 56,
        },
        tabBarLabelStyle: {
          fontSize: 10,
          fontWeight: '700',
        },
      }}
    >
      <Tab.Screen name="Remote" component={FileBrowserScreen} options={{ tabBarLabel: 'Remote', tabBarIcon: ({ color, size }) => <TabIcon route="Remote" color={color} size={size} /> }} />
      <Tab.Screen name="Local" component={LocalBrowserScreen} options={{ tabBarLabel: 'Local', tabBarIcon: ({ color, size }) => <TabIcon route="Local" color={color} size={size} /> }} />
      <Tab.Screen name="Downloads" component={DownloadScreen} options={{ tabBarLabel: 'Downloads', tabBarIcon: ({ color, size }) => <TabIcon route="Downloads" color={color} size={size} /> }} />
      <Tab.Screen name="Sync" component={SyncScreen} options={{ tabBarLabel: 'Sync', tabBarIcon: ({ color, size }) => <TabIcon route="Sync" color={color} size={size} /> }} />
    </Tab.Navigator>
  );
}

function App() {
  const config = useAuthStore(state => state.config);
  const syncRules = useAppStore(state => state.syncRules);
  const setLastSyncTime = useAppStore(state => state.setLastSyncTime);

  const [shareData, setShareData] = React.useState<ShareEventData | null>(null);
  const [sharePath, setSharePath] = React.useState('/');
  const [isUploadingShare, setIsUploadingShare] = React.useState(false);

  React.useEffect(() => {
    // Check initially and set up listener
    if (config) {
      const checkShare = async () => {
        try {
          const data = await getPendingShare();
          if (data) {
            setShareData(data);
          }
        } catch {
          // ignore
        }
      };
      
      checkShare();
      
      const sub = onShareReceived((data) => {
        setShareData(data);
      });
      return () => sub.remove();
    }
  }, [config]);

  const handleUploadShare = async () => {
    if (!shareData || !config) return;
    setIsUploadingShare(true);
    try {
      const service = new AListService(config);
      let targetPath = sharePath.trim();
      if (!targetPath.startsWith('/')) targetPath = '/' + targetPath;

      if (shareData.action === 'send' && shareData.uri) {
        let fileName = 'shared_file';
        try {
          const detail = await localFsService.getFileDetail(shareData.uri);
          if (detail.name) fileName = detail.name;
        } catch {}
        await service.uploadFile(targetPath, shareData.uri, fileName, shareData.mimeType);
      } 
      else if (shareData.action === 'send_multiple' && shareData.uris) {
        for (let i = 0; i < shareData.uris.length; i++) {
          let fileName = `shared_file_${i}`;
          try {
            const detail = await localFsService.getFileDetail(shareData.uris[i]);
            if (detail.name) fileName = detail.name;
          } catch {}
          await service.uploadFile(targetPath, shareData.uris[i], fileName, shareData.mimeType);
        }
      }
      Alert.alert('Upload Complete', 'Shared files uploaded successfully.');
      setShareData(null);
      setSharePath('/');
    } catch (e: any) {
      Alert.alert('Upload Failed', e.message);
    } finally {
      setIsUploadingShare(false);
    }
  };

  const handleCancelShare = () => {
    setShareData(null);
    setSharePath('/');
  };

  // Background Sync logic
  React.useEffect(() => {
    if (!config) return;

    const runSyncTasks = async () => {
      const now = Date.now();
      for (const rule of syncRules) {
        if (!rule.enabled) continue;
        
        const lastSync = rule.lastSyncTime || 0;
        const intervalMs = rule.intervalMinutes * 60 * 1000;
        
        if (now - lastSync >= intervalMs) {
          try {
            await syncService.executeSync(config, rule);
            setLastSyncTime(rule.id, Date.now());
          } catch (e) {
            console.log(`Sync error for rule ${rule.name}:`, e);
          }
        }
      }
    };

    // Run once initially
    runSyncTasks();

    // Check rules every 1 minute
    const interval = setInterval(() => {
      runSyncTasks();
    }, 60000);

    return () => clearInterval(interval);
  }, [config, syncRules, setLastSyncTime]);

  return (
    <SafeAreaProvider>
      <PaperProvider theme={theme}>
        <StatusBar barStyle="dark-content" backgroundColor="#f7f2fa" />
        <NavigationContainer>
          <Stack.Navigator screenOptions={{ headerShown: false }}>
            {config ? (
              <Stack.Screen name="Main" component={MainTabs} />
            ) : (
              <Stack.Screen name="Login" component={LoginScreen} />
            )}
          </Stack.Navigator>
        </NavigationContainer>
        
        <Portal>
          <Dialog visible={!!shareData} onDismiss={handleCancelShare}>
            <Dialog.Title>Upload Shared File</Dialog.Title>
            <Dialog.Content>
              <TextInput
                label="Target Directory Path"
                value={sharePath}
                onChangeText={setSharePath}
                mode="outlined"
                placeholder="/"
              />
            </Dialog.Content>
            <Dialog.Actions>
              <Button onPress={handleCancelShare} disabled={isUploadingShare}>Cancel</Button>
              <Button onPress={handleUploadShare} loading={isUploadingShare} disabled={isUploadingShare}>Upload</Button>
            </Dialog.Actions>
          </Dialog>
        </Portal>
        
      </PaperProvider>
    </SafeAreaProvider>
  );
}

export default App;
