# 需求说明与工程设计文档：独立一键目录同步中心

## 1. 需求背景与核心原则
在 OpenList-Mobile 中引入一个全新的、独立的“目录同步中心”，允许用户在云端与本地设备之间建立严格的 1对1 目录绑定，并通过高度可定制的分屏视图管理和触发同步任务。

**核心设计原则**：
*   **空间独立性**：作为独立的高级功能模块存在，脱离基础的文件浏览（HomeScreen）。
*   **动态直观比对**：采用动态的双栏分屏 UI 设计（支持上下/左右自由切换），同步状态与内容差异一目了然。
*   **映射唯一性（1对1防冲突限制）**：禁止一个本地目录绑定多个云端目录，也禁止一个云端目录绑定多个本地目录，确保同步逻辑的绝对安全与幂等性。

---

## 2. 交互与 UI 设计 (UI & User Flow)

### 2.1 入口与整体布局 (SyncScreen)
*   **入口**：在 `HomeScreen` 的顶部菜单或侧边栏，新增 **[同步中心]** 按钮，点击后通过 Navigation Compose 跳转至独立的 `SyncScreen`。
*   **整体布局**：
    *   **TopBar (顶部控制栏)**：包含返回按钮、当前选中的“同步规则”下拉切换菜单、**[新增同步规则]** 按钮、**[全局设置]**。
    *   **Action Bar (同步操作栏)**：悬浮或固定在底部，显示当前规则状态，包含 **[一键执行同步]** 主按钮。
    *   **Dynamic Split View (动态分屏工作区)**：这是页面的核心视觉区域，包含云端视图、本地视图以及它们之间的“动态接缝”。

### 2.2 动态分屏与接缝切换控件 (Split View & Seam Toggle)
这是为了满足不同屏幕尺寸和用户偏好设计的核心 UI 组件。

*   **布局状态管理**：引入一个 UI 状态 `isVerticalSplit: Boolean`（并在 `SettingsManager` 中持久化该偏好）。
*   **接缝设计 (The Seam)**：
    *   在两块视图交界处绘制一条分割线（`VerticalDivider` 或 `HorizontalDivider`）。
    *   **切换按钮**：在分割线的正中央，悬浮放置一个包含背景的圆形 `IconButton`（图标可使用类似 `Flip` 或 `Rotate` 的标识）。
*   **交互逻辑**：
    *   当用户点击该按钮时，`isVerticalSplit` 状态翻转。
    *   **左右模式 (Horizontal Split)**：使用 `Row` 布局，左侧为云端，右侧为本地，接缝呈垂直状，按钮居中。
    *   **上下模式 (Vertical Split)**：使用 `Column` 布局，上方为云端，下方为本地，接缝呈水平状，按钮居中。
    *   Compose 将通过动画（`AnimatedContent` 或布局切换动画）平滑过渡这两种形态。

### 2.3 视图区功能细化 (Cloud & Local Views)
*   **云端目录视图 (Cloud View)**：顶部显示云端绝对路径。列表展示文件树。在执行 Diff 比对后，文件右侧展示状态角标（如：云端新增⬆️、待覆盖）。
*   **本地目录视图 (Local View)**：顶部显示系统 SAF 路径。UI 风格严格对齐云端视图。比对后展示相应的差异状态角标（如：本地新增⬇️）。

### 2.4 规则切换与防冲突约束 (Rule Switching & Constraint)
*   **快速切换**：通过顶部下拉菜单切换不同的同步规则，下方的双屏视图立即刷新为对应的目录内容。
*   **防冲突创建拦截**：在新建同步规则时，对选择的云端和本地目录进行数据库唯一性校验。若违反“1对1”原则，立刻弹窗报错：“该目录已被其他规则绑定”，阻断创建操作。

---

## 3. 同步规则配置弹窗 (Sync Rule Config Dialog)

当用户点击特定同步规则旁边的 **[设置]**（或在新建规则保存前），将以弹窗或 BottomSheet 的形式展示该配置页。配置项分为三个核心逻辑区块：

### 3.1 基础信息区 (Basic Info)
*   **规则名称 (Rule Name)**: 单行输入框。用户自定义别名（如“我的相册备份”），用于在页面顶部的下拉菜单中快速识别。默认值为本地目录名称。
*   **目录映射 (Directory Mapping - Read Only)**: 两行文本，分别显示绑定的 `云端路径` 和 `本地路径`。下方带有一句灰色提示文案：“*(基于1对1安全映射规则，路径不可在此直接更改，需解绑后重新创建)*”。

### 3.2 同步方向与冲突策略区 (Direction & Conflict Resolution)
这是决定同名文件如何处理的核心区域。

*   **同步模式 (Sync Mode) - 单选按钮组**:
    *   **`云端 ➔ 本地 (单向下载)`**: 仅拉取云端新文件或修改文件。
        *   *子选项 (Checkbox)*：`[ ] 镜像模式：删除本地存在但云端已删除的文件` (危险操作，默认不勾选)。
    *   **`本地 ➔ 云端 (单向上传/备份)`**: 仅推送本地新文件或修改文件。
        *   *子选项 (Checkbox)*：`[ ] 镜像模式：删除云端存在但本地已删除的文件` (危险操作，默认不勾选)。
    *   **`双向同步 (Two-way Sync)`** [默认选项]: 保持两端一致，新文件互相补充。当遇到同名且内容可能不同的文件时，进入冲突处理策略。
*   **冲突处理策略 (Conflict Strategy) - 下拉菜单 (仅选择双向同步时激活)**:
    1.  **`以最新修改时间为准 (Overwrite with Newest)`** [默认]: 比较文件的 `lastModified`，较新的文件覆盖较旧的文件。
    2.  **`跳过冲突文件 (Skip Conflicts)`**: 发现冲突即跳过，在同步报告中标注，由用户手动决定。
    3.  **`始终以云端为准 (Always Cloud Wins)`**: 无条件用云端覆盖本地。
    4.  **`始终以本地为准 (Always Local Wins)`**: 无条件用本地覆盖云端。
*   **比干精度限制 (Diffing Precision) - 开关 Switch**:
    *   `[ ] 仅对比文件大小，忽略修改时间`: 解决 SAF 或云盘时间戳精度或时区偏差问题。只要文件名和字节大小完全一致，即认为文件相同，跳过传输。

### 3.3 自动化触发与条件区 (Automation & Conditions)
定义何时触发底层同步任务。

*   **触发方式 (Trigger Type) - 单选按钮组**:
    1.  **`仅手动同步 (Manual Only)`** [默认]: 仅用户点击“一键同步”时触发，最省电。
    2.  **`定时自动同步 (Periodic Auto-Sync)`**: 激活底层 WorkManager。
        *   *频率下拉菜单*：`每 6 小时` / `每 12 小时` / `每天 1 次`。
*   **网络与环境约束 (Constraints) - Checkbox组 (仅开启自动同步时激活)**:
    1.  `[✓] 仅在 Wi-Fi 网络下执行` (默认开启，防耗流量)。
    2.  `[ ] 仅在设备充电时执行` (推荐深度遍历场景开启)。

---

## 4. 数据模型与架构扩展 (Data Layer)

### 4.1 扩展 Room 数据库：`SyncRule.kt`
在 Room 中增加表结构，利用 SQL 唯一索引强制实施 1对1 约束，并映射所有的用户配置参数。

```kotlin
// --- 枚举定义 ---
enum class SyncMode { DOWNLOAD_ONLY, UPLOAD_ONLY, TWO_WAY }
enum class ConflictStrategy { NEWEST_WINS, SKIP, CLOUD_WINS, LOCAL_WINS }
enum class SyncTrigger { MANUAL, PERIODIC_6H, PERIODIC_12H, PERIODIC_24H }
enum class SyncStatus { IDLE, DIFFING, SYNCING, ERROR }

// --- 实体定义 ---
@Entity(
    tableName = "sync_rules",
    indices = [
        Index(value = ["remotePath"], unique = true), // 数据库层强制云端路径唯一
        Index(value = ["localUri"], unique = true)    // 数据库层强制本地路径唯一
    ]
)
data class SyncRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long, // 关联的 ServerProfile ID
    val ruleName: String, 
    val remotePath: String,
    val localUri: String,
    
    // 方向与冲突策略
    val syncMode: SyncMode = SyncMode.TWO_WAY,
    val isMirrorDeleteEnabled: Boolean = false,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.NEWEST_WINS,
    val ignoreModifiedTime: Boolean = false,

    // 自动化与约束
    val triggerType: SyncTrigger = SyncTrigger.MANUAL,
    val requiresWiFi: Boolean = true,
    val requiresCharging: Boolean = false,
    
    // 状态机
    val lastSyncTime: Long = 0L,
    val status: SyncStatus = SyncStatus.IDLE,
    val errorMsg: String? = null
)
```

### 4.2 ViewModel 与 持久化状态
*   新增 `SyncViewModel`，专职负责加载规则列表、抓取两端文件树并计算差异（`DiffReport`）。
*   **偏好保存**：动态分屏的 UI 状态（上下或左右）交由现有的 `SettingsManager` 保存。

---

## 5. 核心工作流：一键同步的工程实现 (复用现有传输架构)

整个同步过程在工程实现上被解耦为**“内存比对”**与**“底层传输”**两步，最大化复用已有架构：

1.  **内存差异比对 (Diff Computation)**：
    点击“一键同步”后，`SyncViewModel` 拦截点击，更新状态为 `DIFFING`。在后台协程中抓取云端与本地的目录流。根据上述规则配置（如：`ignoreModifiedTime`，`syncMode`）计算出包含待下载、待上传、待删除项的 `DiffReport`，并将结果渲染到双屏 UI 的文件角标上。
2.  **任务降解与队列推入 (Task Delegation)**：
    将 `DiffReport` 中的实质动作，转换为项目现有的 `TransferTask` 实体，批量 `insert` 到现有的 `transfer_tasks` 数据库表中。
3.  **唤醒传输引擎 (Engine Trigger)**：
    调用 `context.startService(Intent(context, TransferService::class.java))` 唤醒现有的 `TransferService`。这使得断点续传、通知栏进度、前台服务保活等核心功能，完全由原有的稳定架构接管。
4.  **UI 进度反馈**：
    `SyncScreen` 通过 Room Flow 监听对应 `TransferTask` 的完成情况，实时更新底部的总进度条与剩余项。