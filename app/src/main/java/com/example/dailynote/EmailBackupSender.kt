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
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class EmailBackupSender(private val context: Context) {

    fun sendDatabaseBackup(config: BackupConfig) {
        val dbFile = context.getDatabasePath(NoteDatabaseHelper.DATABASE_NAME)
        if (!dbFile.exists()) return

        val backupFile = createBackupCopy(dbFile)

        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", config.smtpHost)
            put("mail.smtp.port", config.smtpPort.toString())
            put("mail.smtp.ssl.enable", "true")
        }

        val session = Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(config.senderEmail, config.senderPassword)
            }
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.senderEmail))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.recipientEmail))
            val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            subject = "每日记事 SQLite 备份 - $dateText"
            setContent(buildContent(backupFile), "multipart/mixed")
        }

        Transport.send(message)
    }

    private fun createBackupCopy(dbFile: File): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupName = "${dbFile.nameWithoutExtension}_$timestamp.db"

        val localBackupDir = File(context.filesDir, BACKUP_DIR_NAME).apply { mkdirs() }
        val localBackupFile = File(localBackupDir, backupName)
        dbFile.copyTo(localBackupFile, overwrite = true)
        trimOldBackups(localBackupDir)

        runCatching {
            savePublicBackup(backupName, dbFile)
            trimOldPublicBackups()
        }

        return localBackupFile
    }

    private fun trimOldBackups(backupDir: File) {
        val backups = backupDir.listFiles { file ->
            file.isFile && file.extension.equals("db", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: return

        if (backups.size <= MAX_BACKUP_FILES) return

        backups.drop(MAX_BACKUP_FILES).forEach { it.delete() }
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
        trimOldBackups(publicBackupDir)
    }

    private fun buildContent(dbFile: File): MimeMultipart {
        val textPart = MimeBodyPart().apply {
            setText("这是每日记事应用自动备份的 SQLite 数据库文件。")
        }

        val attachmentPart = MimeBodyPart().apply {
            dataHandler = DataHandler(FileDataSource(dbFile))
            fileName = dbFile.name
        }

        return MimeMultipart().apply {
            addBodyPart(textPart)
            addBodyPart(attachmentPart)
        }
    }

    companion object {
        private const val BACKUP_DIR_NAME = "backups"
        private const val PUBLIC_BACKUP_DIR_NAME = "DailyNoteBackups"
        private const val MAX_BACKUP_FILES = 5
    }
}
