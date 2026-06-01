package com.openlistmobile.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openlistmobile.app.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val ruleId = inputData.getLong(KEY_RULE_ID, INVALID_RULE_ID)
        if (ruleId == INVALID_RULE_ID) {
            return Result.success()
        }

        return try {
            syncRepository.runAutoSync(ruleId)
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }

    companion object {
        const val KEY_RULE_ID = "ruleId"
        private const val INVALID_RULE_ID = -1L
    }
}
