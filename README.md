<div align="center">
  <img src="app/src/main/res/mipmap-xxhdpi/ic_launcher.webp" width="100" />
  <h1>OpenList Mobile</h1>
  <p>
    <img src="https://img.shields.io/badge/Version-v1.4.1-blue" alt="Version">
    <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen" alt="Platform">
    <img src="https://img.shields.io/badge/Language-Kotlin%201.9.23-blue" alt="Language">
    <img src="https://img.shields.io/badge/Framework-Compose%20%7C%20Hilt-orange" alt="Framework">
  </p>
</div>

## 系统摘要 (Overview)

OpenList Mobile 是一款基于 Android 平台开发的 AList 客户端应用。其核心业务逻辑为通过 AList API 与服务器建立连接，实现跨设备的文件系统挂载、目录浏览、文件的上传与下载，以及云端与本地设备的目录同步。该应用支持通过 Android Storage Access Framework (SAF) 提供文档提供程序（DocumentsProvider）接口，允许系统资源管理器及第三方应用直接访问 AList 挂载的文件数据。项目配置的最低系统版本为 Android 8.0 (API Level 26)，目标编译版本为 Android 15 (API Level 35/36)。

## 技术栈与依赖 (Technology Stack & Dependencies)

本项目采用 Android 现代工程标准技术栈进行构建：

*   **核心语言**: Kotlin 1.9.23
*   **构建系统**: Gradle Kotlin DSL
*   **UI 框架**: Jetpack Compose (BOM 2024.05.00), Navigation Compose 2.7.7, Material 3
*   **依赖注入**: Dagger Hilt 2.51.1 (通过 KSP 进行注解处理)
*   **网络请求**: Retrofit 2.11.0, OkHttp 4.12.0 (附带 Logging Interceptor)
*   **本地存储**: Room 2.6.1 (SQLite)
*   **图片加载**: Coil Compose 2.6.0
*   **媒体播放**: Media3 ExoPlayer 1.3.1
*   **文本渲染**: Markwon (Markdown 解析), Prism4j (语法高亮)
*   **并发编程**: Kotlin Coroutines 1.8.0
*   **后台任务**: WorkManager 2.9.0, Hilt Work 1.2.0
*   **系统组件兼容**: AndroidX Core (1.13.1), Lifecycle (2.8.0), DocumentFile (1.0.1)

## 架构设计 (Architecture)

应用采用分层架构模式，遵循单向数据流 (Unidirectional Data Flow) 与 MVVM (Model-View-ViewModel) 架构规范：

*   **UI 层**: 基于 Jetpack Compose 构建声明式 UI。由 `NavHost` 统一管理 `HomeScreen`、`TransferScreen` 与 `SyncScreen` 的路由及转场动画。页面状态由 `HomeViewModel`、`TransferViewModel` 及 `SyncViewModel` 承载，处理用户意图 (Intent) 并将业务逻辑结果映射为 UI State。
*   **Domain 层**: 定义具体的业务抽象，例如 `AuthRepository`、`FileRepository`、`TransferRepository` 与 `SyncRepository`。包含用于计算目录差异的 `DiffEngine`。
*   **Data 层**: 
    *   **远程数据**: 通过 `AListApiService` 定义的 Retrofit 接口与服务器进行 HTTP 通信。
    *   **本地数据**: 利用 Room 数据库维护状态，涵盖上传/下载任务队列 (`TransferTask`)、同步规则 (`SyncRule`) 和服务端点配置 (`ServerProfile`)。
*   **核心服务与组件**:
    *   `TransferService`: 基于 `Service` (Foreground Service Type: `dataSync`) 执行后台文件读写及传输任务，并实时更新通知栏状态。支持多账号隔离传输。
    *   `AListDocumentsProvider`: 继承 Android 标准组件 `DocumentsProvider`，将服务端文件结构适配并暴露给 Android 系统层。
    *   `ProfileContextManager`: 账号上下文管理器，通过 `Mutex` 保证线程安全，支持后台任务与前台操作并行时的账号隔离。
    *   `SyncWorkScheduler` / `SyncWorker`: 基于 `WorkManager` 的后台定时同步调度框架，支持按规则配置触发频率与约束条件。
    *   **安全性设计**: 通过 `KeystoreManager` 调用 Android KeyStore，在硬件层或系统层生成 `AES/GCM/NoPadding` 密钥。获取到的服务端 Token 被加密后再存入 SQLite 数据库，避免明文泄露。

## 安装与下载 (Installation & Download)

**预编译安装包 (Pre-compiled APK)**

本项目在 GitHub Releases 页面提供预编译的 APK 文件。用户可直接下载最新版本并部署至 Android 设备中。

**本地源码编译 (Build from Source)**

如需通过源码进行本地编译以获取 APK 包，请按照以下标准流程操作：

1.  克隆项目代码至本地开发环境。
2.  确保系统中配置 JDK 17，并在 Android Studio (或终端) 中打开项目。
3.  通过 Gradle 执行构建命令以生成 Release 安装包：
    ```bash
    ./gradlew assembleRelease
    ```
4.  生成的 APK 文件存放于 `app/build/outputs/apk/release/` 目录下。随后可通过 `adb install` 命令部署至 Android 设备。

## 应用内配置说明 (In-App Configuration)

应用内的配置参数分别持久化于系统的 `SharedPreferences` 以及 Room 数据库中，具体定义如下：

1.  **SharedPreferences (`app_settings` 实例)**
    *   **参数名称**: `download_dir_uri`
    *   **数据类型**: `String` (Nullable)
    *   **功能映射**: 用于存储用户授权的默认下载目标文件夹的 URI 字符串。底层由 `SettingsManager` 提供读取与写入操作。当发起下载任务时，若该参数存在，系统将使用 DocumentFile 基于此 URI 解析并写入文件。

2.  **Room 数据库 (`server_profiles` 实体表)**
    *   **参数字段**: 
        *   `id` (`Long`): 主键，由数据库自增管理。
        *   `aliasName` (`String`): 配置文件别名。
        *   `serverUrl` (`String`): 目标 AList 服务的基准 URL 地址。
        *   `username` (`String`): AList 身份鉴权的用户名。
        *   `encryptedToken` (`String`): 经 Android KeyStore 加密后的用户 Token。
        *   `isActive` (`Boolean`): 标识当前正在使用的连接配置。
    *   **功能映射**: 用于多服务器环境下的账户凭证管理，允许应用在启动时读取 `isActive = true` 的节点，并使用 `KeystoreManager` 解密其 Token 以完成登录初始化。

## 权限声明 (Permissions Request)

基于 `AndroidManifest.xml` 的定义，应用运行所需的系统权限及业务场景如下：

*   `android.permission.INTERNET`: 建立网络套接字连接，用于 HTTP 请求、获取 AList 数据和执行文件传输。
*   `android.permission.POST_NOTIFICATIONS`: (Android 13+) 授权应用向通知栏发送文件传输任务的进度反馈及结束状态。
*   `android.permission.FOREGROUND_SERVICE`: 授权应用启动前台服务，以避免在执行大文件传输任务时被系统清理机制杀后台。
*   `android.permission.FOREGROUND_SERVICE_DATA_SYNC`: (Android 14+) 明确前台服务的类型为数据同步，以符合更高版本系统的后台运行限制规范。
*   `android.permission.MANAGE_DOCUMENTS`: (针对 Provider) 授权文档提供程序管理文件结构，供系统资源管理器组件进行数据的跨进程访问。

## 编译与构建 (Build Instructions)

应用采用 Gradle 管理依赖和多阶段构建，在项目根目录的终端中可执行如下标准构建任务：

*   **清理已编译文件与缓存**:
    ```bash
    ./gradlew clean
    ```
*   **编译 Debug 版本 (未混淆并附带调试配置)**:
    ```bash
    ./gradlew assembleDebug
    ```
*   **编译 Release 版本 (执行 ProGuard 代码压缩与混淆)**:
    ```bash
    ./gradlew assembleRelease
    ```
注：本项目的构建阶段依赖 KSP (Kotlin Symbol Processing) 来生成 Hilt 依赖注入代码和 Room 数据库实现，需确保本地网络环境能够稳定连接至 Maven Central 与 Google Maven 仓库。

## 待办事项 (TODO / Roadmap)

### 性能与稳定性优化
- [x] ~~**SAF DocumentsProvider 防盗链适配**: 优化 `AListDocumentsProvider` 中的文件流获取逻辑。摒弃硬编码直连，改为调用服务端 `/api/fs/get` 接口获取带有签名的临时直链，确保访问受密码保护或开启了防盗链验证的目录时不会遭遇 403 权限拒绝。~~
- [x] ~~**TransferService 性能优化与内存防抖**: 降低大文件传输过程中对 Room 数据库（SQLite）的高频写操作（当前约每半秒一次）。引入 `MutableStateFlow` 在内存层维护 UI 进度，采用降频或基于状态变更的策略落盘持久化，避免不必要的 I/O 损耗与 Compose 重组卡顿。~~

### 核心功能迭代
- [x] ~~**内置媒体播放中心**: 集成 `androidx.media3:media3-exoplayer`，实现端内的流媒体视频播放及后台音乐音频播放，彻底解决调用外部播放器时的鉴权失败问题。~~
- [x] ~~**全局聚合搜索与过滤**: 接入 AList 的 `/api/fs/search` 接口，提供全局跨目录检索能力，并支持按文件类型（图片、视频、文档）、大小等条件进行精确过滤。~~
- [x] ~~**高级文本与代码预览器**: 升级当前的 `TextPreviewOverlay`，集成第三方渲染库以优美地渲染 Markdown 格式文本，同时为代码文件（如 `.py`, `.js`, `.json` 等）提供语法高亮和行号显示功能。~~
- [x] ~~**指定目录一键同步**: 支持将本地系统文件夹与 AList 指定云端目录进行绑定，通过 Android `WorkManager` 实现后台静默或手动一键同步（支持双向同步或单向备份）。~~
- [x] ~~**批量文件操作**: 支持文件及文件夹的移动与复制功能，支持多选操作。~~
- [x] ~~**直链批量提取**: 支持一键提取并复制带有签名的下载直链。~~
- [x] ~~**Markdown 预览切换**: 文本预览区支持源码与渲染模式无缝切换。~~
- [x] ~~**后台自动静默同步**: 基于 WorkManager 实现定时自动同步，支持 Wi-Fi/充电约束，配合账号隔离机制实现多账号并行同步。~~
- [ ] **自动同步失败重试**: 为后台自动同步补充 `Result.retry()` 策略，网络波动等临时失败时自动重试。
