package com.example.dailynote

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class LocalBackupManager(private val context: Context) {

    fun backupDatabase(): String? {
        val dbFile = context.getDatabasePath(NoteDatabaseHelper.DATABASE_NAME)
        if (!dbFile.exists()) {
            AppFileLogger.error(context, "local_backup", "本地备份失败：数据库文件不存在")
            return null
        }

        val backupName = "${dbFile.nameWithoutExtension}_${timestamp()}_${randomSuffix()}.zip"
        val backupPassword = BackupPreferences(context).loadBackupPassword()
        val saved = savePublicBackup(backupName, dbFile, backupPassword)
        if (!saved) {
            AppFileLogger.error(context, "local_backup", "本地备份失败：保存备份文件失败，file=$backupName")
            return null
        }
        trimOldPublicBackups()
        AppFileLogger.info(context, "local_backup", "本地备份成功，file=$backupName")
        return backupName
    }

    fun publicBackupPathDescription(): String {
        return "${Environment.DIRECTORY_DOCUMENTS}/$PUBLIC_BACKUP_DIR_NAME"
    }


    fun restoreLatestBackupIfExists(): Boolean {
        val dbFile = context.getDatabasePath(NoteDatabaseHelper.DATABASE_NAME)
        val latestBackup = findLatestBackup() ?: run {
            AppFileLogger.info(context, "local_restore", "未找到可用备份，跳过恢复")
            return false
        }

        val backupPassword = BackupPreferences(context).loadBackupPassword()
        return try {
            dbFile.parentFile?.mkdirs()
            BackupZipUtils.extractEncryptedZip(latestBackup, dbFile, backupPassword)
            AppFileLogger.info(context, "local_restore", "自动恢复成功，backup=${latestBackup.name}")
            true
        } catch (e: Exception) {
            AppFileLogger.error(context, "local_restore", "自动恢复失败，backup=${latestBackup.name}", e)
            false
        } finally {
            if (latestBackup.parentFile == context.cacheDir && latestBackup.name.startsWith("daily_note_restore_")) {
                latestBackup.delete()
            }
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    private fun randomSuffix(): String {
        return String.format(Locale.US, "%04d", Random.nextInt(10_000))
    }



    private fun findLatestBackup(): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findLatestBackupOnAndroidQPlus()
        } else {
            findLatestBackupOnLegacyStorage()
        }
    }

    private fun findLatestBackupOnAndroidQPlus(): File? {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf("${Environment.DIRECTORY_DOCUMENTS}/$PUBLIC_BACKUP_DIR_NAME/")
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        val latestUri = context.contentResolver.query(
            filesCollectionUri(),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

            do {
                val displayName = cursor.getString(nameIndex).orEmpty()
                if (!displayName.endsWith(".zip", ignoreCase = true)) {
                    continue
                }
                val id = cursor.getLong(idIndex)
                return@use Uri.withAppendedPath(filesCollectionUri(), id.toString())
            } while (cursor.moveToNext())

            null
        } ?: return null

        val tempFile = File.createTempFile("daily_note_restore_", ".zip", context.cacheDir)
        context.contentResolver.openInputStream(latestUri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: run {
            tempFile.delete()
            return null
        }
        return tempFile
    }

    private fun findLatestBackupOnLegacyStorage(): File? {
        val publicBackupDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            PUBLIC_BACKUP_DIR_NAME
        )

        return publicBackupDir.listFiles { file ->
            file.isFile && file.extension.equals("zip", ignoreCase = true)
        }?.maxByOrNull { it.lastModified() }
    }

    private fun savePublicBackup(backupName: String, dbFile: File, backupPassword: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, backupName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$PUBLIC_BACKUP_DIR_NAME/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(filesCollectionUri(), values) ?: return false
            return runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    writeBackup(dbFile, output, backupPassword)
                } ?: error("无法打开备份输出流")

                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, publishValues, null, null)
                true
            }.getOrElse {
                context.contentResolver.delete(uri, null, null)
                false
            }
        }

        val publicBackupDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            PUBLIC_BACKUP_DIR_NAME
        ).apply { mkdirs() }

        val publicBackupFile = File(publicBackupDir, backupName)
        return runCatching {
            publicBackupFile.outputStream().use { output ->
                writeBackup(dbFile, output, backupPassword)
            }
            true
        }.getOrDefault(false)
    }

    private fun writeBackup(dbFile: File, output: java.io.OutputStream, backupPassword: String) {
        BackupZipUtils.writeEncryptedZip(dbFile, output, backupPassword)
    }

    private fun trimOldPublicBackups() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.RELATIVE_PATH
            )
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf("${Environment.DIRECTORY_DOCUMENTS}/$PUBLIC_BACKUP_DIR_NAME/")

            val backupUris = mutableListOf<Pair<Uri, Long>>()
            context.contentResolver.query(
                filesCollectionUri(),
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val dateModified = cursor.getLong(dateModifiedIndex)
                    val uri = Uri.withAppendedPath(filesCollectionUri(), id.toString())
                    backupUris += uri to dateModified
                }
            }

            if (backupUris.size <= MAX_BACKUP_FILES) return
            backupUris
                .sortedByDescending { it.second }
                .drop(MAX_BACKUP_FILES)
                .forEach { (uri, _) -> context.contentResolver.delete(uri, null, null) }
            return
        }

        val publicBackupDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            PUBLIC_BACKUP_DIR_NAME
        )

        val backups = publicBackupDir.listFiles { file ->
            file.isFile && file.extension.equals("zip", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: return

        if (backups.size <= MAX_BACKUP_FILES) return
        backups.drop(MAX_BACKUP_FILES).forEach { it.delete() }
    }

    companion object {
        private const val PUBLIC_BACKUP_DIR_NAME = "DailyNoteBackups"
        private const val MAX_BACKUP_FILES = 5
    }

    private fun filesCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }
    }
}
