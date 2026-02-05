package com.example.dailynote

import android.content.Context
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
            setContent(buildContent(dbFile), "multipart/mixed")
        }

        Transport.send(message)
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
}
