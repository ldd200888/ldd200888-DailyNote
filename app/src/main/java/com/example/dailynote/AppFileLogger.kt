package com.example.dailynote

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppFileLogger {
    private const val LOG_FILE = "daily_note.log"
    private const val TAG = "AppFileLogger"
    private const val MAX_LOG_SIZE_BYTES = 1024 * 1024 // 1MB
    private const val BACKUP_DIR_NAME = "DailyNoteBackups"

    fun info(context: Context, event: String, message: String) {
        write(context, "INFO", event, message, null)
    }

    fun error(context: Context, event: String, message: String, throwable: Throwable? = null) {
        write(context, "ERROR", event, message, throwable)
    }

    @Synchronized
    private fun write(
        context: Context,
        level: String,
        event: String,
        message: String,
        throwable: Throwable?
    ) {
        runCatching {
            val logFile = logFile(context)
            rotateIfTooLarge(logFile)

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val builder = StringBuilder()
                .append(timestamp)
                .append(" [")
                .append(level)
                .append("] ")
                .append(event)
                .append(" - ")
                .append(message)

            if (throwable != null) {
                builder.append("\n")
                    .append(Log.getStackTraceString(throwable))
            }
            builder.append("\n")

            logFile.appendText(builder.toString())
        }.onFailure {
            Log.e(TAG, "写入日志文件失败", it)
        }
    }

    private fun logFile(context: Context): File {
        val dir = resolveBackupDir(context).apply { mkdirs() }
        return File(dir, LOG_FILE)
    }

    private fun resolveBackupDir(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
                BACKUP_DIR_NAME
            )
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                BACKUP_DIR_NAME
            )
        }
    }

    private fun rotateIfTooLarge(logFile: File) {
        if (!logFile.exists() || logFile.length() < MAX_LOG_SIZE_BYTES) {
            return
        }
        val backup = File(logFile.parentFile, "$LOG_FILE.bak")
        if (backup.exists()) {
            backup.delete()
        }
        logFile.renameTo(backup)
        logFile.writeText("")
    }
}
