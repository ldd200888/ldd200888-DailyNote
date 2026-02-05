package com.example.dailynote

import android.content.Context
import java.util.Calendar

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
            backupMinute = prefs.getInt(KEY_BACKUP_MINUTE, 0),
            backupWeekdays = parseWeekdays(
                prefs.getString(KEY_BACKUP_WEEKDAYS, defaultWeekdaysText()).orEmpty()
            )
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
            .putString(KEY_BACKUP_WEEKDAYS, config.backupWeekdays.sorted().joinToString(","))
            .apply()
    }

    private fun parseWeekdays(text: String): Set<Int> {
        return text.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
            .toSet()
            .ifEmpty { defaultWeekdays() }
    }

    private fun defaultWeekdaysText(): String = defaultWeekdays().sorted().joinToString(",")

    private fun defaultWeekdays(): Set<Int> = setOf(
        Calendar.SUNDAY,
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY
    )

    companion object {
        private const val PREF_NAME = "backup_config"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_SENDER_EMAIL = "sender_email"
        private const val KEY_SENDER_PASSWORD = "sender_password"
        private const val KEY_RECIPIENT_EMAIL = "recipient_email"
        private const val KEY_BACKUP_HOUR = "backup_hour"
        private const val KEY_BACKUP_MINUTE = "backup_minute"
        private const val KEY_BACKUP_WEEKDAYS = "backup_weekdays"
    }
}
