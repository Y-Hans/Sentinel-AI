package com.sentinel.ai.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Placeholder WorkManager worker for background intelligence sync.
 */
class BackgroundSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Placeholder: sync threat intelligence feeds in a later phase.
        return Result.success()
    }
}
