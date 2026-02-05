package com.example.dailynote

import android.content.Context

class BackupPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): BackupConfig {
        return BackupConfig(
            smtpHost = prefs.getString(KEY_SMTP_HOST, "smtp.163.com").orEmpty(),
            smtpPort = prefs.getInt(KEY_SMTP_PORT, 465),
            senderEmail = prefs.getString(KEY_SENDER_EMAIL, "").orEmpty(),
            senderPassword = prefs.getString(KEY_SENDER_PASSWORD, "").orEmpty(),
            recipientEmail = prefs.getString(KEY_RECIPIENT_EMAIL, "").orEmpty(),
            backupHour = prefs.getInt(KEY_BACKUP_HOUR, 2),
            backupMinute = prefs.getInt(KEY_BACKUP_MINUTE, 0)
        )
    }

    fun saveConfig(config: BackupConfig) {
        prefs.edit()
            .putString(KEY_SMTP_HOST, config.smtpHost)
            .putInt(KEY_SMTP_PORT, config.smtpPort)
            .putString(KEY_SENDER_EMAIL, config.senderEmail)
            .putString(KEY_SENDER_PASSWORD, config.senderPassword)
            .putString(KEY_RECIPIENT_EMAIL, config.recipientEmail)
            .putInt(KEY_BACKUP_HOUR, config.backupHour)
            .putInt(KEY_BACKUP_MINUTE, config.backupMinute)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "backup_config"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_SENDER_EMAIL = "sender_email"
        private const val KEY_SENDER_PASSWORD = "sender_password"
        private const val KEY_RECIPIENT_EMAIL = "recipient_email"
        private const val KEY_BACKUP_HOUR = "backup_hour"
        private const val KEY_BACKUP_MINUTE = "backup_minute"
    }
}
