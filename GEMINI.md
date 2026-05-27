# OpenList-Mobile: Native AList Android Client

## Project Overview
OpenList-Mobile is a high-performance, native Android client for [AList](https://github.com/alist-org/alist) and OpenList servers. It aims to provide a smooth (60/120fps), native file browsing experience, integrating deeply with the Android system (e.g., DocumentsProvider, SAF, Share Sheet).

### Key Technologies
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material 3](https://m3.material.io/).
- **Language**: Kotlin.
- **Architecture**: Clean Architecture (Data, Domain, UI layers) + MVVM.
- **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android).
- **Networking**: [Retrofit 2](https://square.github.io/retrofit/) & [OkHttp 4](https://square.github.io/okhttp/).
- **Persistence**: [Room](https://developer.android.com/training/data-storage/room).
- **Asynchrony**: Kotlin [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://kotlinlang.org/docs/flow.html).
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/).
- **Background Tasks**: Foreground Service (for downloads) & [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager).
- **Security**: [Android Keystore](https://developer.android.com/training/articles/keystore) for encrypting tokens.

## Building and Running
- **Requirements**: Android Studio (Koala or later), Android SDK (Target SDK 36, Min SDK 26).
- **Build**: `./gradlew assembleDebug`
- **Install**: `./gradlew installDebug`
- **Tests**: `./gradlew test` (Unit tests), `./gradlew connectedAndroidTest` (Instrumented tests).

## Architecture & Project Structure
The project follows a modular structure within the `app` module:

- `com.example.alist`
    - `data/`: Implementation of data sources.
        - `local/`: Room database, DAOs, Entities, and Keystore logic.
        - `remote/`: Retrofit API services and DTOs (Models).
        - `repository/`: Repository implementations bridging data sources.
    - `domain/`: Business logic.
        - `repository/`: Repository interfaces.
        - `model/`: Domain models (if separate from DTOs).
    - `ui/`: Presentation layer.
        - `components/`: Reusable Compose components.
        - `home/`: Main file browser screen, ViewModel, and Login logic.
        - `transfer/`: Upload/Download management screen and ViewModel.
        - `theme/`: Material 3 theme definitions (Color, Shape, Type).
    - `di/`: Hilt modules for Dependency Injection.
    - `service/`: Android Services (e.g., `DownloadService`).
    - `provider/`: Content Providers (e.g., `AListDocumentsProvider`).
    - `utils/`: Utility classes for Permissions, Token management, Sharing, etc.

## Development Conventions
- **State Management**: Use `StateFlow` in ViewModels to expose UI state. Use `update` to modify state atomically.
- **UI Components**: Prefer reusable components in `ui/components`. Use `MaterialTheme` for colors and typography to ensure MD3 compliance.
- **Error Handling**: Use `Result<T>` or similar wrappers for repository responses.
- **Concurrency**: Always perform network or database operations on `Dispatchers.IO`. Use `viewModelScope` for coroutines launched from ViewModels.
- **Responsive UI**: Support different screen sizes using `WindowSizeClass`. Avoid hardcoded dimensions; use `dp` and `sp` and follow Material 3 spacing guidelines.
- **Security**: Never store passwords or tokens in plain text. Use `KeystoreManager` for encryption.

## Key Files
- `需求文档.md`: Original Product Requirement Document (PRD).
- `plan.md`: Initial development and milestones plan.
- `ui_plan.md`: Detailed UI/UX design and refactoring plan.
- `app/build.gradle.kts`: Project dependencies and configuration.
- `MainActivity.kt`: Entry point and navigation host.
