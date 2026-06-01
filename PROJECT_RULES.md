# PROJECT_RULES.md

本文件定义 OpenList Mobile 项目的技术栈、目录规范、架构约定、编码风格与 AI 协作规则。所有后续代码生成、重构、功能实现与文档编写都必须遵守本文件。

## 1. 项目概览

OpenList Mobile 是一个 Android 原生客户端，用于连接 AList/OpenList 服务，实现文件浏览、上传下载、媒体播放、文档提供器访问，以及本地目录与云端目录同步。

项目采用 Kotlin + Jetpack Compose + MVVM + Repository 分层架构，强调最小改动、清晰数据流、可维护同步逻辑，以及对 Android 后台运行限制的兼容。

## 2. 核心技术栈与版本

### Android

- 最低系统版本：Android 8.0，API 26
- 编译 SDK：API 35
- 目标 SDK：API 36
- Java 版本：17
- Kotlin JVM Target：17
- 构建系统：Gradle Kotlin DSL

### 语言与 UI

- Kotlin：1.9.23
- Jetpack Compose BOM：2024.05.00
- Material 3
- Navigation Compose：2.7.7
- Activity Compose：1.9.0

### 依赖注入

- Dagger Hilt：2.51.1
- Hilt Navigation Compose：1.2.0
- KSP：用于 Hilt 与 Room 代码生成

### 网络与数据

- Retrofit：2.11.0
- OkHttp：4.12.0
- Gson：2.10.1
- Room：2.6.1
- Kotlin Coroutines：1.8.0
- Flow：用于响应式数据流

### 文件与媒体

- Android DocumentFile：1.0.1
- Android Storage Access Framework，SAF
- Coil Compose：2.6.0
- Media3 ExoPlayer：1.3.1
- Media3 UI：1.3.1
- Media3 Session：1.3.1

### 文本预览

- Markwon：4.6.2
- Prism4j：2.0.0

## 3. 目录结构规范

主代码目录：

```text
app/src/main/java/com/openlistmobile/app/
```

推荐结构如下：

```text
app/
  src/
    main/
      java/com/openlistmobile/app/
        data/
          local/
          remote/
          repository/
        domain/
          repository/
          sync/
        di/
        provider/
        service/
        ui/
          components/
          home/
          sync/
          transfer/
        utils/
      res/
        drawable/
        mipmap-*/
        values/
        xml/
    test/
      java/com/openlistmobile/app/
    androidTest/
      java/com/openlistmobile/app/
```

### data/local

放置 Room 数据库相关代码。

包括：

- Entity
- DAO
- Room Database
- TypeConverter
- Migration
- 本地持久化模型

命名示例：

- `AppDatabase.kt`
- `SyncRule.kt`
- `SyncRuleDao.kt`
- `TransferTask.kt`
- `ServerProfile.kt`

规则：

- Entity 不应包含复杂业务逻辑。
- DAO 只负责数据库读写，不包含 UI 或网络逻辑。
- 数据库字段变更必须同步考虑 Migration。
- enum 持久化必须通过统一 TypeConverter 处理。

### data/remote

放置远程 API、请求响应模型、网络辅助类。

包括：

- Retrofit API Service
- DTO / response model
- request body
- 网络层 model

命名示例：

- `AListApiService.kt`
- `AListFile.kt`
- `ProgressRequestBody.kt`

规则：

- 远程 DTO 不直接承担 UI 状态职责。
- 网络错误应在 Repository 层转换为清晰的 `Result` 或异常信息。
- 不在 UI 层直接调用 Retrofit。

### data/repository

放置 Repository 实现类。

命名规则：

- 接口在 `domain/repository`
- 实现在 `data/repository`
- 实现类使用 `Impl` 后缀

示例：

- `SyncRepositoryImpl.kt`
- `TransferRepositoryImpl.kt`
- `FileRepositoryImpl.kt`

规则：

- Repository 是 UI/ViewModel 与数据源之间的边界。
- Repository 可以协调 Room、Retrofit、SAF、Service。
- Repository 对外优先暴露 `Flow`、`suspend fun`、`Result<T>`。
- 不在 Repository 中持有 Compose 状态。

### domain/repository

放置领域层接口与轻量数据结构。

示例：

- `SyncRepository.kt`
- `TransferRepository.kt`
- `FileRepository.kt`

规则：

- domain 接口不依赖 Android UI。
- domain 接口表达业务能力，不暴露具体实现细节。
- 接口命名必须稳定，避免为单一调用创建过度抽象。

### domain/sync

放置同步核心算法与同步领域模型。

示例：

- `DiffEngine.kt`
- `DiffReport.kt`
- `SyncNode.kt`

规则：

- 差异计算逻辑必须尽量保持纯函数。
- `DiffEngine` 不应直接访问网络、数据库、文件系统。
- 文件扫描、任务入队、删除执行等 I/O 操作放在 Repository。
- 同步规则应复用 `SyncRule` 中的配置：`syncMode`、`conflictStrategy`、`isMirrorDeleteEnabled`、`ignoreModifiedTime`。

### di

放置 Hilt Module。

示例：

- `DatabaseModule.kt`
- `RepositoryModule.kt`
- `NetworkModule.kt`

规则：

- 所有 Repository、DAO、网络服务、全局工具类应通过 Hilt 注入。
- 不在普通业务代码中手动 new 复杂依赖。
- Application 级别依赖安装到 `SingletonComponent`。

### provider

放置 Android Provider 组件。

示例：

- `AListDocumentsProvider.kt`

规则：

- Provider 代码要严格注意权限、URI、异常处理。
- 不应把 Provider 写成 UI 或 ViewModel 的扩展。
- Provider 中的远程访问必须考虑认证、403、直链签名、防盗链等场景。

### service

放置 Android Service、Foreground Service、MediaSessionService 等后台组件。

示例：

- `TransferService.kt`
- `MediaPlaybackService.kt`

规则：

- 长时间文件传输必须符合 Android 前台服务限制。
- 数据同步类前台服务应使用 `foregroundServiceType="dataSync"`。
- Service 不直接承担 UI 状态管理。
- Service 应通过数据库或 Repository 与其他层通信。

### ui

放置 Jetpack Compose UI 与 ViewModel。

推荐按页面或功能域拆分：

```text
ui/
  components/
  home/
  sync/
  transfer/
```

规则：

- `ui/components` 放跨页面复用组件。
- 页面专用组件放在对应页面目录下。
- ViewModel 与 Screen 保持同目录。
- UI 只负责展示状态与转发用户意图，不直接执行复杂 I/O。
- Composable 函数应尽量保持小而清晰。

### utils

放置通用工具类。

示例：

- `SettingsManager.kt`
- `TokenManager.kt`
- `RemoteLinkBuilder.kt`
- `KeystoreManager.kt`

规则：

- utils 中只放确实跨模块复用的工具。
- 不要把业务逻辑随意塞进 utils。
- 如果逻辑只属于同步、传输或文件模块，应放回对应 domain/data 层。

### res

放置 Android 资源。

规则：

- 图标、图片放 `drawable` 或 `mipmap-*`。
- XML 配置放 `res/xml`。
- 字符串、颜色、主题等放 `values`。
- 不要在 Kotlin 文件中硬编码大量可本地化 UI 文案，除非当前项目已有明确模式。

## 4. 架构与数据流规范

项目采用 MVVM + Repository + 单向数据流。

标准数据流：

```text
UI Event
  -> ViewModel
  -> Repository Interface
  -> Repository Impl
  -> Room / Retrofit / SAF / Service
  -> Flow / Result
  -> ViewModel StateFlow
  -> Compose UI
```

### UI 层

- Compose UI 只观察 `StateFlow` 或 Compose State。
- UI 事件通过 ViewModel 方法触发。
- 不在 Composable 中直接调用 DAO、Retrofit、Service 复杂逻辑。
- 不在 Composable 中启动长时间后台任务。
- Dialog、Sheet、Screen 的状态应由 ViewModel 或局部 `remember` 管理，按影响范围选择。

### ViewModel 层

- ViewModel 负责组合 UI 状态。
- ViewModel 使用 `viewModelScope.launch` 调用 suspend 方法。
- ViewModel 暴露不可变 `StateFlow`。
- Mutable 状态必须私有化。

推荐模式：

```kotlin
private val _uiState = MutableStateFlow(SomeUiState())
val uiState: StateFlow<SomeUiState> = _uiState.asStateFlow()
```

### Repository 层

- Repository 是数据访问和业务执行的主要入口。
- Repository 可以处理线程切换，优先使用 `Dispatchers.IO` 执行 I/O。
- Repository 返回 `Result<T>` 表达可恢复失败。
- Repository 不返回 Compose 类型。
- Repository 不依赖具体 Screen。

### Room 数据流

- 列表型数据优先使用 `Flow<List<T>>`。
- 单次查询使用 `suspend fun`。
- 写操作必须在协程中执行。
- 不在主线程访问数据库。

### 同步数据流

手动同步现有流程：

```text
选择 SyncRule
  -> 扫描云端目录与本地 SAF 目录
  -> DiffEngine.computeDiff
  -> UI 预览差异
  -> 用户确认
  -> enqueueDiff
  -> TransferTask 入队
  -> TransferService 执行上传/下载
```

自动同步目标流程：

```text
WorkManager 周期触发
  -> 根据 ruleId 读取 SyncRule
  -> 校验规则、账号、SAF 权限、网络/充电约束
  -> computeDiff
  -> 按静默策略处理冲突与删除
  -> enqueueDiff
  -> 唤醒 TransferService
  -> 更新 lastSyncTime/status/errorMsg
```

## 5. 状态管理规范

### UI State

每个主要页面应定义独立 UI State data class。

示例：

```kotlin
data class SyncUiState(
    val rules: List<SyncRule> = emptyList(),
    val selectedRule: SyncRule? = null,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val message: String? = null
)
```

规则：

- UI State 使用不可变 data class。
- 更新状态使用 `copy`。
- 不暴露可变集合。
- 错误消息、加载状态、选中项都应明确建模。

### 一次性事件

- 简单提示可保存在 `message: String?` 中，并提供 clear 方法。
- 复杂一次性事件可使用 Channel/SharedFlow，但不要过度引入。

### 业务状态

同步状态使用现有 enum：

```kotlin
enum class SyncStatus {
    IDLE,
    DIFFING,
    SYNCING,
    ERROR
}
```

传输状态使用现有 `TransferStatus`。

规则：

- 不用字符串硬编码业务状态。
- 新增状态必须确认 UI、数据库、迁移和测试都同步更新。

## 6. 编码风格规范

### Kotlin 通用规范

- 使用 Kotlin idiomatic 写法。
- 优先使用 `val`，只有确实需要重新赋值时使用 `var`。
- 函数保持短小，单一职责。
- 避免不必要的抽象、接口、工厂、管理器。
- 不写 speculative code，不为未来可能需求提前设计复杂扩展。
- 不吞掉异常，除非明确是可忽略场景，并应留下简短说明。
- 避免使用 `!!`，除非上下文能严格保证非空。
- 复杂 I/O 必须放在 `Dispatchers.IO`。

### 命名规范

类名：

- PascalCase
- 示例：`SyncRepositoryImpl`、`TransferService`

函数名：

- lowerCamelCase
- 动词开头，表达行为
- 示例：`computeDiff`、`enqueueDiff`、`startTransferEngine`

变量名：

- lowerCamelCase
- 布尔值使用 `is`、`has`、`requires`、`should` 前缀
- 示例：`isLoading`、`hasTransfers`、`requiresWiFi`

常量名：

- `const val` 使用 UPPER_SNAKE_CASE
- 示例：`ACTION_START`、`EXTRA_TASK_ID`

Composable：

- PascalCase
- 必须使用名词或页面名
- 示例：`SyncScreen`、`CloudDirPickerDialog`

ViewModel：

- 页面名 + `ViewModel`
- 示例：`SyncViewModel`、`HomeViewModel`

Repository：

- 接口：`XxxRepository`
- 实现：`XxxRepositoryImpl`

DAO：

- Entity 名 + `Dao`
- 示例：`SyncRuleDao`

### Compose 规范

- Composable 函数保持无副作用，副作用使用 `LaunchedEffect`、`DisposableEffect` 等。
- 页面级 Composable 接收 UI State 和事件回调。
- 可复用组件放 `ui/components`。
- 页面私有小组件可以放在同一个 Screen 文件底部，并设为 `private`。
- 不在 Composable 中执行网络请求或数据库写入。
- 不在 Composable 中直接启动 Service，除非项目已有同类局部模式且改动非常小。

### 协程规范

- UI 层使用 `viewModelScope`。
- Repository 内部 I/O 使用 `withContext(Dispatchers.IO)`。
- Service 中长任务使用 `SupervisorJob` 或受控 CoroutineScope。
- 避免使用 `GlobalScope`。
- Flow 收集必须考虑生命周期或作用域。

### 错误处理规范

- 用户可感知失败应转为明确中文错误信息。
- Repository 可使用 `Result.failure(e)` 返回失败。
- ViewModel 负责将错误转换成 UI message。
- 后台任务失败必须记录到状态字段，例如 `status = ERROR`、`errorMsg = ...`。
- 不允许静默失败导致用户无法判断功能是否执行。

## 7. 同步功能专项规则

### SyncRule

`SyncRule` 是同步规则的核心配置来源。

已有关键字段：

- `profileId`
- `ruleName`
- `remotePath`
- `localUri`
- `syncMode`
- `isMirrorDeleteEnabled`
- `conflictStrategy`
- `ignoreModifiedTime`
- `triggerType`
- `requiresWiFi`
- `requiresCharging`
- `lastSyncTime`
- `status`
- `errorMsg`

规则：

- 不要绕过 `SyncRule` 单独存储重复配置。
- 新增同步配置字段时，必须考虑 Room migration。
- 本地目录与云端目录当前保持唯一映射，不要破坏唯一索引语义。

### DiffEngine

规则：

- `DiffEngine` 必须保持纯计算。
- 不在 `DiffEngine` 中访问 Room、Retrofit、SAF、Context。
- 新增 Diff 行为必须补充单元测试。
- 双向同步默认不做镜像删除。
- 镜像删除只在单向同步中生效。

### 自动同步

自动同步必须满足：

- 使用 Android 推荐后台调度机制，优先 WorkManager。
- 不要求精确触发时间。
- 不在后台弹出确认弹窗。
- 不破坏手动同步流程。
- 自动同步必须记录成功或失败状态。
- 自动同步必须尊重 `requiresWiFi` 和 `requiresCharging`。
- 自动同步必须复用现有 Diff 和 TransferTask 入队逻辑。
- 长时间传输必须遵守 Android 前台服务限制。

### 删除与覆盖

- 镜像删除是高风险行为，必须由用户显式开启。
- 自动同步中执行镜像删除前，规则配置必须已经允许。
- 双向同步不得执行镜像删除。
- 覆盖行为必须由 `syncMode` 和 `conflictStrategy` 决定。
- 不允许在没有规则依据的情况下删除文件。

## 8. 数据库与迁移规则

- 修改 Entity 字段必须同步更新数据库版本。
- 新增字段必须提供合理默认值。
- Migration SQL 必须与 Entity 字段完全一致。
- enum 字段新增枚举值时，必须确认旧数据兼容。
- 不随意删除旧表或字段。
- `fallbackToDestructiveMigration()` 存在时也不能依赖破坏性迁移解决正常升级问题。

## 9. 网络与认证规则

- 所有 AList/OpenList API 调用必须经过统一网络层。
- Token 管理由 `TokenManager` 或既有认证组件处理。
- 不在日志、异常、UI 中泄露 token。
- 构造远程下载链接应复用 `RemoteLinkBuilder`。
- 认证失效时应返回明确错误，不要无限重试。

## 10. SAF 与文件访问规则

- 本地目录访问必须基于 SAF URI。
- 用户选择目录后必须尝试持久化 URI 权限。
- 后台同步前必须考虑 URI 权限是否仍有效。
- 文件写入优先使用 `DocumentFile` 或 `ContentResolver`。
- 不假设本地路径一定是普通文件系统路径。
- 不绕过 SAF 直接访问用户授权目录。

## 11. Service 与后台任务规则

- 大文件上传下载由 `TransferService` 执行。
- `TransferService` 必须以前台服务方式运行。
- 通知渠道必须存在。
- 后台任务不直接做长时间阻塞主线程操作。
- WorkManager 负责任务调度，TransferService 负责实际传输。
- 不创建多个互相竞争的后台执行器。

## 12. 测试规范

### 单元测试

适合单元测试的内容：

- `DiffEngine`
- 路径拼接
- 冲突策略
- 同步模式决策
- 纯函数工具方法

### 集成测试或人工测试

适合人工验证的内容：

- SAF 授权
- AList 真实网络访问
- 前台服务通知
- 大文件上传下载
- WorkManager 实际调度
- Android 后台限制兼容性

### 测试命名

测试函数名应描述行为。

示例：

```kotlin
@Test
fun download_only_pulls_remote_only_files() {
    ...
}
```

## 13. AI 协作规则

### 最高优先级规则：禁止自动编译、执行或构建

在任何情况下，AI 都不得为了验证代码而自动执行以下行为：

- 不得运行 Gradle 构建命令
- 不得运行编译命令
- 不得运行单元测试命令
- 不得运行 Android instrumentation test
- 不得启动应用
- 不得安装 APK
- 不得执行会触发项目构建、编译、打包、测试的命令

禁止命令包括但不限于：

```text
./gradlew build
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew test
./gradlew :app:testDebugUnitTest
./gradlew connectedAndroidTest
adb install
adb shell am start
```

原因：

- 用户的服务器或本地硬件资源有限。
- 所有生成代码将由用户手动审查和测试。
- AI 只能进行静态检查、阅读代码、分析逻辑和说明预期验证方式。

AI 可以做的事情：

- 阅读文件
- 搜索代码
- 修改代码
- 生成文档
- 做静态逻辑检查
- 指出建议由用户手动执行的验证命令
- 说明需要人工测试的场景

AI 输出中必须明确说明：

- 未自动编译
- 未自动运行测试
- 需要用户手动验证的点

### 变更原则

- 只修改与任务直接相关的文件。
- 不做无关重构。
- 不顺手格式化无关代码。
- 不删除无关旧代码。
- 不引入未请求的新功能。
- 不创建一次性抽象。
- 优先沿用项目已有模式。

### 需求不清晰时

如果需求存在多个解释，AI 必须先说明理解和假设。  
如果选择会影响数据安全、删除文件、后台行为或用户隐私，必须先询问用户。  
如果只是小范围实现细节，可基于现有代码风格做保守选择。

### 输出语言

无论用户使用中文还是英文提示词，AI 都必须使用中文反馈。  
代码标识符、文件名、命令和 API 名称保持原文。

## 14. 文档规则

- 面向用户的说明使用中文。
- 技术名词可以保留英文原文。
- README、需求文档、实现计划应保持清晰、可执行。
- TODO 状态必须与实际代码能力一致。
- 完成功能后再更新 Roadmap 勾选状态。

## 15. 安全规则

- 不泄露 token、密码、服务器地址等敏感信息。
- 不在日志中打印完整认证信息。
- 不自动执行 destructive 操作。
- 涉及删除文件、覆盖文件、清空数据库、重置 Git 等操作时，必须明确获得用户确认。
- 自动同步中的删除行为必须严格受 `isMirrorDeleteEnabled` 和 `syncMode` 控制。

## 16. Git 规则

- 不自动执行 `git reset --hard`。
- 不自动丢弃用户修改。
- 不覆盖未理解的已有改动。
- 提交前应说明变更范围。
- 如果工作区已有无关改动，应保持原样。

## 17. 代码审查检查清单

提交或交付代码前，AI 应做静态自查：

- 是否只改了相关文件
- 是否符合现有目录结构
- 是否破坏现有手动同步流程
- 是否引入不必要抽象
- 是否遗漏 Room migration
- 是否处理错误状态
- 是否存在 token 泄露风险
- 是否错误绕过 SAF
- 是否错误执行后台长任务
- 是否违反“禁止自动编译/执行/构建”规则

## 18. 自动同步第一版推荐边界

自动同步第一版应优先实现：

- 同步规则中启用周期配置
- 根据规则注册或取消 WorkManager 周期任务
- 后台读取规则并计算 Diff
- 静默入队上传/下载任务
- 唤醒现有 TransferService
- 更新 `lastSyncTime`
- 更新 `status` 和 `errorMsg`
- 尊重 Wi-Fi 与充电约束

第一版不优先实现：

- 精确时间点同步
- 实时文件监听
- 文件历史版本
- 冲突可视化后台通知
- 复杂同步日志表
- 多设备同步状态合并
- 内容哈希全量校验

原则：

先复用现有稳定链路，再逐步增强可观测性和配置能力。
