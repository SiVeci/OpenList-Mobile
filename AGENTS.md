# AGENTS.md

## Project

OpenList Mobile — mobile-optimized AList/OpenList client built with React Native (Bare, New Architecture). Android-only focus, minSdk 28.

## Commands

- `npm start` — Metro bundler
- `npm run android` — build & run on connected device/emulator (requires Android SDK + Java 17+)
- `npm run typecheck` — TypeScript check (`tsc --noEmit`)
- `npm run lint` — ESLint
- `npm test` — Jest

No iOS development is currently supported.

## Architecture

```
index.js → src/app/App.tsx (PaperProvider + NavigationContainer + BottomTabs)
             ├─ LoginScreen (no auth)
             └─ MainTabs (authenticated)
                 ├─ Remote  → FileBrowserScreen
                 │              ├─ FileItem / FileIcon
                 │              ├─ SortSheet / FilterSheet
                 │              ├─ ActionSheet → PreviewModal
                 │              └─ BatchActionBar
                 ├─ Local   → LocalBrowserScreen (SAF file browser)
                 ├─ Downloads → DownloadScreen (native foreground service)
                 └─ Sync    → SyncScreen (auto-sync rules)

src/services/alistService.ts — all AList REST API calls (axios)
src/services/localFsService.ts — local FS via SAF native module
src/services/downloadService.ts — native background download
src/services/syncService.ts — remote↔local sync engine
src/stores/authStore.ts — Zustand + AsyncStorage persist
src/stores/appStore.ts — sync rules + download state
src/native/ — TS bridge declarations for Kotlin native modules
src/types/index.ts — shared TypeScript interfaces
```

State management: **Zustand** with `persist` middleware. Auth config, sort preferences, and sync rules in AsyncStorage.

## Critical Quirks

- **Path alias**: `@/*` → `src/*` configured in both `tsconfig.json` and `babel-plugin-module-resolver` (in `babel.config.js`)
- **React 19 peer dep conflicts**: `react-native-fast-image`, `react-native-pdf`, `react-native-send-intent` do NOT support React 19 yet. They were removed from dependencies. Use `Image` from RN core, `Linking.openURL()` for PDF, and `Linking.openURL('vlc://...')` for external players.
- **react-native-document-picker**: Same React 19 peer dep issue. Upload uses a placeholder alert until Phase 2 SAF native module is built.
- **react-native-vector-icons**: Must keep `implementation project(':react-native-vector-icons')` in `android/app/build.gradle`. RN Paper depends on `MaterialCommunityIcons` font.
- **External player URLs**: VLC → `vlc://`, MX Player → `intent:` scheme, nPlayer → `nplayer-` prefix. Player preference stored in AsyncStorage key `openlist_pref_player`.
- **AList API auth**: Every request sends both `Authorization` and `AList-Token` headers with the token value.

## Native Modules (Kotlin)

All custom native modules are in `android/app/src/main/java/com/openlistmobile/` and must be manually registered in `MainApplication.kt` (add to `PackageList`):

| Module | Package | Purpose |
|---|---|---|
| `SAFModule` | `SAFPackage` | Storage Access Framework: pick dir, list/read/write/delete files |
| `DownloadModule` | `DownloadPackage` | Foreground service download with notification progress |
| `ShareModule` | `SharePackage` | Receives shared files from other apps |

**DownloadService** is an Android ForegroundService (`dataSync` type). Requires `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` permissions in AndroidManifest.

**ShareReceiverActivity** is a separate Activity (not a React Activity) that captures `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intents, stores pending data in a static field, launches the main app, and finishes. `ShareModule.getPendingShare()` reads and clears the pending data.

## Android Config

- `minSdkVersion = 28` (set in `android/build.gradle` ext block)
- `android:requestLegacyExternalStorage="true"` in AndroidManifest for compatibility
- `android:usesCleartextTraffic` controlled by Gradle property (allows HTTP in debug)
- Permissions: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `WRITE_EXTERNAL_STORAGE` (maxSdk 28), `READ_EXTERNAL_STORAGE` (maxSdk 32)
- ShareReceiverActivity registered with `ACTION_SEND` + `ACTION_SEND_MULTIPLE` intent-filters for `*/*` MIME type

## Naming Conventions

- Components: PascalCase files in `src/components/`
- Screens: PascalCase with "Screen" suffix in `src/app/`
- Stores: camelCase with "Store" suffix in `src/stores/`
- Native modules: PascalCase + "Module" suffix in `android/.../java/` + `Package` wrapper
- No comments in code unless explicitly requested
