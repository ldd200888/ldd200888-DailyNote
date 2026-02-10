package com.example.dailynote

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeByMediaStore(context, builder.toString())
            } else {
                writeByFile(builder.toString())
            }
        }.onFailure {
            Log.e(TAG, "写入日志文件失败", it)
        }
    }

    private fun writeByMediaStore(context: Context, logEntry: String) {
        val uri = ensureLogUri(context)
        rotateIfTooLarge(context, uri)
        val bytes = logEntry.toByteArray(Charsets.UTF_8)
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).channel.use { channel ->
                channel.position(descriptor.statSize.coerceAtLeast(0L))
                channel.write(ByteBuffer.wrap(bytes))
                channel.force(true)
            }
        } ?: error("无法打开日志输出流")
    }

    private fun ensureLogUri(context: Context): Uri {
        val existingUri = findExistingLogUri(context)
        if (existingUri != null) {
            return existingUri
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, LOG_FILE)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR_NAME/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(filesCollectionUri(), values)
            ?: error("创建日志文件失败")

        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null
        )
        return uri
    }

    private fun findExistingLogUri(context: Context): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR_NAME/", LOG_FILE)

        context.contentResolver.query(filesCollectionUri(), projection, selection, selectionArgs, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val id = cursor.getLong(idIndex)
            return Uri.withAppendedPath(filesCollectionUri(), id.toString())
        }
        return null
    }

    private fun writeByFile(logEntry: String) {
        val logFile = resolveLegacyLogFile()
        rotateIfTooLarge(logFile)
        logFile.appendText(logEntry)
    }

    private fun resolveLegacyLogFile(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "$BACKUP_DIR_NAME/$LOG_FILE"
        ).apply {
            parentFile?.mkdirs()
        }
    }

    private fun rotateIfTooLarge(context: Context, uri: Uri) {
        val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        if (size < MAX_LOG_SIZE_BYTES) {
            return
        }
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).channel.use { channel ->
                channel.truncate(0)
                channel.force(true)
            }
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

    private fun filesCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }
    }
}
