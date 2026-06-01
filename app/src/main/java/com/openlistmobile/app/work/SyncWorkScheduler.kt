package com.openlistmobile.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.openlistmobile.app.data.local.SyncRule
import com.openlistmobile.app.data.local.SyncTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncWorkScheduler @Inject constructor(
    @ApplicationContext context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun upsertRuleWork(rule: SyncRule) {
        val intervalHours = repeatIntervalHours(rule.triggerType)
        if (intervalHours == null) {
            cancelRuleWork(rule.id)
            return
        }

        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours, TimeUnit.HOURS)
            .setInputData(workDataOf(SyncWorker.KEY_RULE_ID to rule.id))
            .setConstraints(buildConstraints(rule))
            .addTag(uniqueWorkName(rule.id))
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName(rule.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelRuleWork(ruleId: Long) {
        workManager.cancelUniqueWork(uniqueWorkName(ruleId))
    }

    private fun buildConstraints(rule: SyncRule): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(if (rule.requiresWiFi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(rule.requiresCharging)
            .build()
    }

    private fun repeatIntervalHours(trigger: SyncTrigger): Long? {
        return when (trigger) {
            SyncTrigger.MANUAL -> null
            SyncTrigger.PERIODIC_6H -> 6L
            SyncTrigger.PERIODIC_12H -> 12L
            SyncTrigger.PERIODIC_24H -> 24L
        }
    }

    private fun uniqueWorkName(ruleId: Long): String {
        return "$WORK_NAME_PREFIX$ruleId"
    }

    companion object {
        private const val WORK_NAME_PREFIX = "sync_rule_"
    }
}
