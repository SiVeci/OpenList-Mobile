# 全局 UI 美化与重构执行计划 (ui_plan.md)

## 🎯 核心架构原则 (The Prime Directives)
1. **MD3 原生化**：全面摒弃硬编码色值与尺寸。所有的颜色、字体、圆角必须从 `MaterialTheme.colorScheme`、`typography` 和 `shapes` 中提取。
2. **逻辑绝对隔离**：本次重构仅限于 `Composable` 纯 UI 层与 `Modifier` 的视觉修饰。对于现有的 `ViewModel` 状态流（StateFlow/LiveData）、网络请求、Room 数据库、WorkManager 及挂载核心代码，实行**“零触碰”**原则。
3. **大屏/折叠屏优先 (Responsive First)**：引入 `WindowSizeClass`，彻底告别单列拉伸。UI 架构必须具备响应式能力（Compact / Medium / Expanded），在大屏设备上自动升维。

---

## 🧬 第一部分：视觉基因提取与系统定义 (Visual DNA)
基于提供的参考图，我们提取出以下全局设计规范，这将在 Phase 1 中严格实现：

*   **色彩体系 (Color Scheme)**：
    *   **Primary (主色调)**：高饱和度的靛蓝色/紫蓝色（参考：Login 按钮、选中状态的 Filter Chip）。
    *   **Background/Surface (背景/表面)**：使用极其干净的灰白色 / Off-white (参考：整体背景与卡片的高对比度区分)。
    *   **Error/Destructive (警告色)**：明亮的红色（参考：多选状态下的删除按钮）。
    *   **SurfaceVariant (次级表面)**：浅灰蓝，用于列表的选中态背景。
*   **形体规范 (Shapes)**：
    *   **Small (8-12dp)**：用于状态标签（如 SSL Tag）、过滤器的 Chips。
    *   **Medium (16dp)**：全局通用卡片圆角，用于文件列表项（List Item）、输入框（TextField）。
    *   **Large (24-32dp)**：用于底部面板 (BottomSheet) 的顶部圆角、登录主控制台卡片。
    *   **Extra Large / Stadium (全圆角)**：用于悬浮操作按钮 (FAB)、底部多选悬浮控制栏 (Contextual Action Bar)。
*   **排版与留白 (Typography & Spacing)**：
    *   字体偏向现代无衬线体（Sans-serif），大量使用字重（Weight）来区分层级（如标题加粗，辅助信息常规）。
    *   元数据（日期、文件大小）使用 `labelSmall` 或 `bodySmall`，配合较低的 Alpha 透明度。
    *   全局标准外边距设定为 16dp，卡片间距 8-12dp，保持高度的呼吸感。

---

## 📅 执行阶段划分 (Execution Phases)

### 🛠 Phase 1：全局设计系统重建 (Design System Foundation)
**目标**：搭建基础画板，重写全局 Theme，为后续的组件组装提供符合 MD3 的“积木”。
*   **Step 1.1**：定义明暗两套调色板 (`LightColorScheme` & `DarkColorScheme`)，映射提取的靛蓝主色、Surface 颜色及警告色。
*   **Step 1.2**：重写 `AppTypography`，统一定义 Title, Body, Label 的字号、字重与行高。
*   **Step 1.3**：重写 `AppShapes`，映射 16dp (Medium), 24dp (Large) 等圆角规范。
*   **Step 1.4**：构建统一的 `AppTheme` Composable 容器，替换掉项目中所有原有的旧版 Theme 或无主题包裹的 UI。

### 🧱 Phase 2：核心组件精装修 (Core Components Refinement)
**目标**：打造高频复用的基础组件，确保交互反馈（如水波纹）的现代化。
*   **Step 2.1 - 现代化 TopAppBar**：结合参考图设计，集成圆角搜索框 (Search Bar)、右侧的筛选/视图切换 IconButton，以及下面一层的路径面包屑 (Breadcrumbs) 导航。
*   **Step 2.2 - 通用卡片式 ListItem**：
    *   重构文件列表项：16dp 圆角卡片结构，去除生硬分割线。
    *   实现**选中状态修饰符 (Selection Modifier)**：当项被选中时，背景平滑过渡至 `PrimaryContainer`（浅蓝色），左侧图标叠加带圆角的勾选标记（Checkmark）。
    *   确保规范的 Ripple（涟漪）点击反馈。
*   **Step 2.3 - 统一的 BottomSheet 容器**：封装带顶部拖拽指示器（Drag Handle）和 28dp 大圆角的现代化 MD3 ModalBottomSheet，用于后续的排序和筛选面板。
*   **Step 2.4 - 悬浮上下文操作栏 (Floating Contextual Action Bar)**：精准复刻图 3/4 中的底部黑色胶囊控制栏。包含左侧关闭按钮、已选数量统计、以及右侧的下载/复制/删除动作组，并加入弹出/隐藏的动画。

### 📱 Phase 3：主界面与响应式改造 (Main Screen & Responsive Overhaul)
**目标**：实现页面级的美化，并注入折叠屏/平板的灵魂。
*   **Step 3.1 - 响应式基建 (Window Size Classes)**：
    *   在主界面顶层引入 `calculateWindowSizeClass()`。
    *   **Compact (手机外屏/普通直板机)**：维持标准的单列卡片列表 (LazyColumn) + BottomSheet 交互。
    *   **Medium/Expanded (折叠屏展开态/平板)**：
        *   **布局升维**：自动将单列列表切换为 `LazyVerticalGrid` (自适应列数网格视图)，或采用 `List-Detail` 双栏布局（左侧文件树/列表，右侧预览/详情）。
        *   **面板降级**：在大屏下，将 BottomSheet 替换为右侧的侧边属性栏 (Side Panel) 或优雅的下拉弹窗 (Dropdown/Pop-up)，避免大屏下 BottomSheet 造成的视觉浪费。
*   **Step 3.2 - 筛选与排序引擎 UI 升级**：
    *   利用 Phase 2 的 BottomSheet/Side Panel，重构参考图中的 Filter/Sort 界面。
    *   实现单选 Segmented Buttons 和多选 Chips (圆角矩形，选中态实心，未选中态描边)。
*   **Step 3.3 - 占位图与加载状态**：设计统一的空白状态 (Empty State) 页面（插画+引导文案），以及列表加载时的 Shimmer 骨架屏或现代化的 Pull-to-refresh 动画。

### 🚀 Phase 4：传输与预览中心优化 (Transfer & Preview Center Optimization)
**目标**：提升特定功能模块的视觉精致度。
*   **Step 4.1 - 传输列表视图升级**：
    *   引入带有圆角端点的 MD3 `LinearProgressIndicator`。
    *   优化速度 (MB/s)、剩余时间、状态（下载中/暂停/失败）的排版，使用颜色和字重区分主次信息。
*   **Step 4.2 - 纯文本/媒体预览沉浸化**：
    *   使用 `WindowCompat.setDecorFitsSystemWindows` 实现 Edge-to-Edge 沉浸式全屏预览。
    *   针对纯文本预览，优化阅读体验（行高、段落间距、可切换的浅色/深色阅读模式背景）。