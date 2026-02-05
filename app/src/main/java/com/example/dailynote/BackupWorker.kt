package com.example.dailynote

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val config = BackupPreferences(applicationContext).loadConfig()
        if (config.senderEmail.isBlank() || config.senderPassword.isBlank() || config.recipientEmail.isBlank()) {
            return Result.retry()
        }

        return runCatching {
            EmailBackupSender(applicationContext).sendDatabaseBackup(config)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
