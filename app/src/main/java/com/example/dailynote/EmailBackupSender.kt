package com.example.dailynote

import android.content.Context
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import kotlin.random.Random
import javax.activation.DataHandler
import javax.mail.util.ByteArrayDataSource
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
        if (!dbFile.exists()) {
            AppFileLogger.error(context, "email_backup", "邮箱备份失败：数据库文件不存在")
            return
        }

        val backupName = "${dbFile.nameWithoutExtension}_${timestamp()}_${randomSuffix()}.zip"
        val backupPassword = BackupPreferences(context).loadBackupPassword()
        val backupZipBytes = ByteArrayOutputStream().use { output ->
            BackupZipUtils.writeEncryptedZip(dbFile, output, backupPassword)
            output.toByteArray()
        }

        val useSsl = config.smtpPort == 465
        val props = Properties().apply {
            put("mail.transport.protocol", "smtp")
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", config.smtpHost)
            put("mail.smtp.port", config.smtpPort.toString())
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
            if (useSsl) {
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.ssl.trust", config.smtpHost)
                put("mail.smtp.socketFactory.port", config.smtpPort.toString())
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
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
            setContent(buildContent(backupName, backupZipBytes), "multipart/mixed")
        }

        try {
            Transport.send(message)
            AppFileLogger.info(context, "email_backup", "邮箱备份发送成功，recipient=${config.recipientEmail}")
        } catch (e: Exception) {
            val detail = formatSmtpError(e)
            AppFileLogger.error(context, "email_backup", detail, e)
            throw RuntimeException(detail, e)
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    private fun randomSuffix(): String {
        return String.format(Locale.US, "%04d", Random.nextInt(10_000))
    }

    private fun buildContent(backupName: String, backupZipBytes: ByteArray): MimeMultipart {
        val textPart = MimeBodyPart().apply {
            setText("这是每日记事应用自动备份的数据库压缩包（ZIP）。")
        }

        val attachmentPart = MimeBodyPart().apply {
            dataHandler = DataHandler(ByteArrayDataSource(backupZipBytes, "application/zip"))
            fileName = backupName
        }

        return MimeMultipart().apply {
            addBodyPart(textPart)
            addBodyPart(attachmentPart)
        }
    }

    private fun formatSmtpError(error: Exception): String {
        val causeList = mutableListOf<String>()
        generateSequence(error.cause) { it.cause }
            .forEach { causeList.add("${it::class.java.simpleName}: ${it.message.orEmpty()}") }

        if (error is javax.mail.MessagingException) {
            var next = error.nextException
            while (next != null) {
                causeList.add("${next::class.java.simpleName}: ${next.message.orEmpty()}")
                next = (next as? javax.mail.MessagingException)?.nextException
            }
        }

        val causes = causeList.toList()
        val causeText = if (causes.isNotEmpty()) {
            causes.joinToString(separator = " -> ")
        } else {
            "无"
        }
        return buildString {
            append("SMTP发送失败：")
            append(error::class.java.simpleName)
            append(": ")
            append(error.message.orEmpty())
            append(" | 根因：")
            append(causeText)
        }
    }

}
