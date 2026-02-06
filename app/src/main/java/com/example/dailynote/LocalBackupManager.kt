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

class LocalBackupManager(private val context: Context) {

    fun backupDatabase(): String? {
        val dbFile = context.getDatabasePath(NoteDatabaseHelper.DATABASE_NAME)
        if (!dbFile.exists()) return null

        val backupName = "${dbFile.nameWithoutExtension}_${timestamp()}.db"
        savePublicBackup(backupName, dbFile)
        trimOldPublicBackups()
        return backupName
    }

    fun publicBackupPathDescription(): String {
        return "${Environment.DIRECTORY_DOCUMENTS}/$PUBLIC_BACKUP_DIR_NAME"
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    private fun savePublicBackup(backupName: String, dbFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, backupName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$PUBLIC_BACKUP_DIR_NAME")
            }

            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return
            context.contentResolver.openOutputStream(uri)?.use { output ->
                dbFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: context.contentResolver.delete(uri, null, null)
            return
        }

        val publicBackupDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            PUBLIC_BACKUP_DIR_NAME
        ).apply { mkdirs() }

        val publicBackupFile = File(publicBackupDir, backupName)
        dbFile.copyTo(publicBackupFile, overwrite = true)
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
                MediaStore.Files.getContentUri("external"),
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
                    val uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
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
            file.isFile && file.extension.equals("db", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: return

        if (backups.size <= MAX_BACKUP_FILES) return
        backups.drop(MAX_BACKUP_FILES).forEach { it.delete() }
    }

    companion object {
        private const val PUBLIC_BACKUP_DIR_NAME = "DailyNoteBackups"
        private const val MAX_BACKUP_FILES = 5
    }
}
