# AI Agent 专属开发指令与执行计划

**【系统指令（System Prompt）】**
你现在是一位资深的 Android 原生开发架构师。我们将基于《AList/OpenList 原生安卓客户端 PRD》从零开发一款 App。
**核心开发纪律：**

1. **模块化与接口优先**：在实现任何具体功能前，必须先定义好 Data Model（数据模型）、Repository（仓库接口）和 ViewModel 的骨架，保留好 TODO 注释。
2. **渐进式交付**：我们将分 Phase（阶段）进行开发。你必须在完成当前 Phase 的代码输出，并确保代码无语法错误、能够成功编译成 APK 后，停下来等待我的指令，再进入下一个 Phase。
3. **架构规范**：采用 Clean Architecture (UI 层, Domain 层, Data 层) + MVVM 架构。使用 Hilt 进行依赖注入。
4. **UI 与主题**：`targetSdkVersion` 设定为 36。使用 Jetpack Compose 构建 UI，**严格采用 Material 3 默认配色方案（基于系统动态取色或默认蓝/紫基调）**，不要自定义复杂的调色板。注意窗口尺寸类（Window Size Classes）的响应式布局。

---

## 阶段一：工程初始化与基础骨架（里程碑：可编译的极简 APK）

**目标**：搭建基础工程，配置依赖库，跑通第一个带 Material 3 默认主题的空白页面。

**执行任务**：

1. **构建配置**：配置 `build.gradle.kts`，引入依赖：Jetpack Compose, Material 3, Navigation Compose, Hilt, Retrofit2, OkHttp4, Room, Coil, Kotlin Coroutines。
2. **目录结构初始化**：建立清晰的分包：`di` (依赖注入), `data` (网络与数据库), `domain` (业务逻辑), `ui` (界面组件)。
3. **基础组件配置**：
* 配置 Hilt 的 Application 类。
* 配置基础的 `MaterialTheme`（仅使用默认设置）。
* 编写一个包含 `Scaffold` 和顶部导航栏（TopAppBar）的空白 `HomeScreen`。


4. **网络层占位**：创建全局 OkHttp Client 的单例配置框架，预留“忽略 SSL 证书错误”的拦截器（Interceptor）接口。
5. **编译测试**：输出当前所有代码。确保此时可以直接编译打出 APK。

---

## 阶段二：数据层与账户管理搭建（里程碑：实现接口与本地存储连通）

**目标**：搞定多服务器配置的存储和 AList API 的打通。

**执行任务**：

1. **本地数据库 (Room)**：建立 `ServerProfile` 实体类（包含服务器地址、账号、Token 等），并编写 Dao 接口。
2. **凭据加密 (Android Keystore)**：提供 `KeystoreManager` 单例工具类，实现对 Token 的加解密，并在 Room 存取时调用。
3. **网络接口定义 (Retrofit)**：根据 AList API 文档，编写 `AListApiService`。优先实现 `/api/auth/login` 和 `/api/fs/list`。
4. **仓库层 (Repository)**：编写 `AuthRepository` 和 `FileRepository`，桥接 Room 和 Retrofit。
5. **ViewModel 联调**：创建 `HomeViewModel`，实现调用仓库层获取文件列表的逻辑，暴露 `StateFlow` 给 UI 层。

---

## 阶段三：核心 UI 实现——文件浏览器 MVP（里程碑：看到真实的云端文件）

**目标**：实现文件列表的展示与服务器切换。

**执行任务**：

1. **服务器切换组件**：在首页顶部实现下拉菜单（DropdownMenu），读取 Room 中的配置文件列表，支持切换并触发刷新。
2. **文件列表渲染**：使用 `LazyColumn` 渲染文件树。根据文件类型（type）赋予内置静态图标（文件夹、视频、图片、文档）。
3. **基础交互加载**：接入 SwipeRefreshLayout（下拉刷新），并实现列表滚动到底部时的分页加载逻辑封装。
4. **编译测试**：确保 App 能够连接真实服务器，并顺滑滚动查看目录树。

---

## 阶段四：多维筛选、排序与本地缓存完善

**目标**：实现大类/后缀筛选及缓存优化。

**执行任务**：

1. **筛选器 UI**：在列表顶部添加 `FilterChip` 组（视频/图片/音频/文档/其他）及一个后缀名输入框（OutlinedTextField）。
2. **本地过滤与排序逻辑**：在 ViewModel 中实现大类+精确后缀过滤逻辑，以及按名称/大小/时间排序的逻辑，实现“文件夹始终置顶”。
3. **Room 目录缓存**：修改 `FileRepository`，成功请求列表后写入 Room，下次进入该目录时优先从本地秒开读取，后台静默拉取更新。

---

## 阶段五：文件基础操作与媒体预览

**目标**：实现文件级交互与第三方调用。

**执行任务**：

1. **CRUD 菜单**：为列表项添加长按/点击更多菜单（新建文件夹、重命名、删除、复制、移动），并在 Repository 中对接对应 API。
2. **调用第三方播放器**：点击视频时，提取带 Token 的直链，通过 `Intent.createChooser` 唤起外部专业播放器。
3. **图片与文档预览**：点击图片时打开原生的 Coil 全屏查看页面；点击文档时，拼接在线预览前缀并调用系统浏览器预览。

---

## 阶段六：后台传输引擎 (WorkManager & Foreground Service)

**目标**：实现原生级稳定上传下载。

**执行任务**：

1. **服务搭建**：基于 `Foreground Service` 构建下载引擎，声明现代 Android 数据同步前置服务权限。
2. **通知栏对接**：使用 `NotificationManager` 在通知栏实时更新传输进度条。
3. **断点续传逻辑**：使用 `OkHttp` 的 Range 请求头实现分片下载与断点续传。
4. **传输可视化**：新建传输管理页面，采用 Tab 行（正在传输、已完成、失败），连接 Room 数据库展示队列。

---

## 阶段七：系统级深度整合 (DocumentsProvider & Share Sheet)

**目标**：实现与 Android 系统的无缝互通。

**执行任务**：

1. **分享面板集成 (Share Sheet)**：配置 `<intent-filter>` 接收 `ACTION_SEND` 意图，实现将外部文件分享至本 App 并触发 AList 上传。
2. **DocumentsProvider 挂载**：继承 `DocumentsProvider`，重写相关 `query` 方法，将 AList 逻辑桥接为系统 `Cursor`，实现系统自带“文件”App 侧边栏挂载。
3. **分区存储适配**：使用 SAF 或 MediaStore 将下载完成的文件合法写入系统 Download 公共目录。