# 自动同步实现计划（后台定时静默同步）

## 概要

在不改动现有手动同步算法的前提下，把“规则配置 -> 扫描两端 -> 计算 Diff -> 可选镜像删除 -> 入队传输 -> TransferService 执行”这条链路复用到 WorkManager 周期任务里。

本计划按你刚刚确认的方向设计为“按规则账号”执行，不依赖当前前台活动账号。为避免现有全局 `TokenManager` 和全局传输队列串号，第一版会补齐两件事：

- 自动同步执行时，按 `SyncRule.profileId` 恢复该规则的账号上下文。
- 传输任务持久化 `profileId`，让 `TransferService` 在执行上传前切回正确账号。

同时保持这些边界不变：

- 不做实时监听。
- 不做精确时间同步。
- 不做复杂同步日志表。
- 不改手动同步的预览/确认交互。

## 实现步骤与数据流

1. 用户保存同步规则时，`SyncRule` 新增/编辑表单会真正写入 `triggerType`、`requiresWiFi`、`requiresCharging`。
2. `SyncRepositoryImpl.createRule/updateRule/deleteRule` 在数据库操作成功后，直接调用 `SyncWorkScheduler` 同步 WorkManager 任务。
3. `SyncWorkScheduler` 为每条非手动规则维护唯一周期任务，命名为 `sync_rule_<id>`。
4. WorkManager 触发 `SyncWorker` 后，`SyncWorker` 只做薄调度：读 `ruleId`，调用 `syncRepository.runAutoSync(ruleId)`。
5. `runAutoSync(ruleId)` 负责完整静默同步流程：
   - 读取最新 `SyncRule`
   - 若规则不存在或已改回 `MANUAL`，直接结束
   - 通过 `profileId` 恢复账号上下文
   - 校验本地 URI 权限与本地目录可访问性
   - 写入规则状态 `DIFFING`
   - 调用现有 `computeDiff(rule)`
   - 若规则允许镜像删除，则直接执行 `executeMirrorDeletes(rule, diff)`
   - 调用 `enqueueDiff(rule, diff)` 创建传输任务
   - 若有传输任务则唤醒 `TransferService`
   - 成功后写入 `lastSyncTime`、`status = IDLE`、`errorMsg = null`
   - 失败时写入 `status = ERROR` 和错误信息，不更新 `lastSyncTime`
6. `enqueueDiff` 生成的每条 `TransferTask` 都带上 `profileId`。
7. `TransferService` 在执行每个上传任务前，根据任务自己的 `profileId` 恢复对应账号上下文，再继续上传。
8. `SyncScreen` 展示当前选中规则的自动同步配置、上次同步时间、状态、错误信息。
9. `SyncRuleConfigSheet` 在用户开启“自动同步 + 镜像删除”时，保存前弹一次风险确认，文案明确“会静默删除多余文件”。

伪代码如下：

```text
onSaveRule(rule):
  savedRule = repo.save(rule)
  scheduler.upsertOrCancel(savedRule)

SyncWorker.doWork(ruleId):
  repo.runAutoSync(ruleId)
  return Result.success()

runAutoSync(ruleId):
  rule = syncRuleDao.getRuleById(ruleId) ?: return
  if rule.triggerType == MANUAL: return
  profileContext.withProfile(rule.profileId) {
    validateLocalUri(rule)
    updateStatus(rule.id, DIFFING)
    diff = computeDiff(rule)
    if rule.isMirrorDeleteEnabled: executeMirrorDeletes(rule, diff)
    taskIds = enqueueDiff(rule, diff with profileId)
    if taskIds.notEmpty: startTransferEngine()
    markSuccess(rule.id, now)
  }.onFailure {
    markError(rule.id, message)
  }

TransferService.process(task):
  if task.profileId > 0: profileContext.applyProfile(task.profileId)
  if task.type == UPLOAD: uploadFile(task)
  else: downloadFile(task)
```

## 计划修改的文件

- [app/build.gradle.kts](G:/GitHub/OpenList-Mobile/app/build.gradle.kts)：新增 WorkManager 与 Hilt Work 依赖。
- [app/src/main/java/com/openlistmobile/app/AListApplication.kt](G:/GitHub/OpenList-Mobile/app/src/main/java/com/openlistmobile/app/AListApplication.kt)：接入 `HiltWorkerFactory`，让 WorkManager 能注入 Worker。
- [app/src/main/java/com/openlistmobile/app/data/local/TransferTask.kt](G:/GitHub/OpenList-Mobile/app/src/main/java/com/openlistmobile/app/data/local/TransferTask.kt)：新增 `profileId` 字段。
- [app/src/main/java/com/openlistmobile/app/data/local/AppDatabase.kt](G:/GitHub/OpenList-Mobile/app/src/main/java/com/openlistmobile/app/data/local/AppDatabase.kt)：数据库版本升级并添加 `TransferTask` 的迁移。
- [app/src/main/java/com/openlistmobile/app/data/local/SyncRuleDao.kt](G:/GitHub/OpenList-Mobile/app/src/main/java/com/openlistmobile/app/data/local/SyncRuleDao.kt)：补充一次性查询和同步结果更新方法。
- [app/src/main/java/com/openlistmobile/app/data/local/ServerProfileDao.kt](G:/GitHub/OpenList-Mobile/app/src/main/java/com/openlistmobile/app/data/local/ServerProfileDao.kt)：补充按 `id` 查询 profile。
- [app/src/main/java/com/openlistmobile/app/domain/repository/SyncRepository.kt](G:/GitHub/OpenList-Mobile/app/src/main/java/com/openlistmobile/app/domain/repository/SyncRepository.kt)：新增后台自动同步入口。
- [app/src/main/java/com/openlistmobile/app/data/repository/SyncRepositoryImpl.kt](G:/GitHub/OpenList-Mobile/app/src/main/java/com/openlistmobile/app/data/repository/SyncRepositoryImpl.kt)：实现自动同步主流程，并在规则增删改时同步调度任务。
- [app/src/main/java/com/openlistmobile/app/service/TransferService.kt](G:/GitHub/OpenList-Mobile/app/src/main/java/com/openlistmobile/app/service/TransferService.kt)：按任务恢复账号上下文。
- [app/src/main/java/com/openlistmobile/app/ui/home/HomeViewModel.kt](G:/GitHub/OpenList-Mobile/app/src/main/java/com/openlistmobile/app/ui/home/HomeViewModel.kt)：应用启动后的规则调度兜底对齐。
- [app/src/main/java/com/openlistmobile/app/ui/sync/SyncRuleConfigSheet.kt](G:/GitHub/OpenList-Mobile/app/src/main/java/com/openlistmobile/app/ui/sync/SyncRuleConfigSheet.kt)：新增周期、Wi-Fi、充电控件与镜像删除风险确认。
- [app/src/main/java/com/openlistmobile/app/ui/sync/SyncScreen.kt](G:/GitHub/OpenList-Mobile/app/src/main/java/com/openlistmobile/app/ui/sync/SyncScreen.kt)：展示自动同步状态摘要。

新增文件：

- `app/src/main/java/com/openlistmobile/app/work/SyncWorker.kt`
- `app/src/main/java/com/openlistmobile/app/work/SyncWorkScheduler.kt`
- `app/src/main/java/com/openlistmobile/app/utils/ProfileContextManager.kt`

## 需要新增的函数与职责

`SyncWorkScheduler`

- `upsertRuleWork(rule: SyncRule)`
- `cancelRuleWork(ruleId: Long)`
- `reconcileAllRules()`
- `buildConstraints(rule: SyncRule): Constraints`
- `repeatIntervalHours(trigger: SyncTrigger): Long`
- `uniqueWorkName(ruleId: Long): String`

`SyncRepository`

- `runAutoSync(ruleId: Long): Result<AutoSyncResult>`

`SyncRepositoryImpl`

- `runAutoSync(ruleId: Long)`
- `markRuleSyncSuccess(ruleId: Long, completedAt: Long)`
- `markRuleSyncError(ruleId: Long, message: String)`
- `validateLocalAccess(rule: SyncRule)`
- `enqueueDiffWithProfile(rule: SyncRule, report: DiffReport)` 或直接在现有 `enqueueDiff` 中补 `profileId`
- `syncRuleSchedule(rule: SyncRule)` 或直接在 `create/update/delete` 内调用 scheduler

`ProfileContextManager`

- `withProfile(profileId: Long, block: suspend () -> T): T`
- `applyProfile(profileId: Long): Result<Unit>`
- `captureCurrentContext()`
- `restoreContext(snapshot)`

`SyncRuleDao`

- `getAllRulesOnce(): List<SyncRule>`
- `updateLastSyncTime(id: Long, lastSyncTime: Long)`

`ServerProfileDao`

- `getProfileById(id: Long): ServerProfile?`

`TransferService`

- `ensureTaskProfile(task: TransferTask)`

## 关键实现决策

- WorkManager 只负责“定时触发”和“调用自动同步入口”，不直接执行大文件传输。
- 真正耗时的上传下载继续由现有 `TransferService` 完成，这样前台通知和 dataSync 语义复用现有能力。
- 自动同步失败后，Worker 返回成功结束，不做立即 `retry()`；下一次周期再重试，避免瞬时故障导致重复刷状态。
- `lastSyncTime` 只在一次自动同步流程成功完成后更新，包括“无差异”这种 no-op 成功。
- 镜像删除在自动模式下尊重规则配置，第一版直接执行，但 UI 必须明确告知是静默删除。
- 为兼容老任务，`TransferTask.profileId` 迁移默认值用 `-1`；`TransferService` 看到 `-1` 时沿用当前活动账号，仅作为旧数据兜底。

## 测试与验收场景

- 新建 `PERIODIC_6H` 规则后，存在唯一 `sync_rule_<id>` 周期任务。
- 编辑规则周期或约束后，旧任务被替换，新任务生效。
- 删除规则或改回 `MANUAL` 后，对应任务被取消。
- 自动同步在 `DOWNLOAD_ONLY`、`UPLOAD_ONLY`、`TWO_WAY` 下都能复用现有 Diff 逻辑。
- 自动同步遇到 `SKIP` 冲突策略时不入队冲突文件。
- 自动同步无差异时不入队任务，但更新 `lastSyncTime`。
- 本地 URI 权限失效、账号不存在、token 解密失败、网络失败时，规则状态写为 `ERROR`。
- 上传任务在多账号场景下按任务 `profileId` 恢复正确账号，不依赖当前前台账号。
- 手动“同步预览 -> 确认同步”流程保持不变。

## 默认假设

- 第一版只保证后台自动同步之间不会串号，并保证传输队列按任务账号执行。
- 前台如果在自动同步执行窗口内同时做跨账号操作，仍可能短暂共享全局 `TokenManager`；本版通过 `ProfileContextManager` 降低风险，但不把仓库层整体改造成“完全无全局登录态”。
- 自动同步状态展示放在当前规则详情区域，而不是规则下拉列表中。
- 不新增 `SyncRunLog` 表，结果摘要只在内存返回，持久化先只写 `lastSyncTime/status/errorMsg`。
