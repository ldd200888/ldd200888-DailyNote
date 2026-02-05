package com.example.dailynote

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 兼容历史任务；当前版本不再使用 WorkManager 自动备份。
 */
class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return Result.success()
    }
}
