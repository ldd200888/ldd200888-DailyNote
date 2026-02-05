package com.example.dailynote

import android.content.Context
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): BackupConfig {
        return BackupConfig(
            smtpHost = prefs.getString(KEY_SMTP_HOST, "smtp.163.com").orEmpty(),
            smtpPort = prefs.getInt(KEY_SMTP_PORT, 465),
            senderEmail = prefs.getString(KEY_SENDER_EMAIL, "").orEmpty(),
            senderPassword = prefs.getString(KEY_SENDER_PASSWORD, "").orEmpty(),
            recipientEmail = prefs.getString(KEY_RECIPIENT_EMAIL, "").orEmpty()
        )
    }

    fun saveConfig(config: BackupConfig) {
        prefs.edit()
            .putString(KEY_SMTP_HOST, config.smtpHost)
            .putInt(KEY_SMTP_PORT, config.smtpPort)
            .putString(KEY_SENDER_EMAIL, config.senderEmail)
            .putString(KEY_SENDER_PASSWORD, config.senderPassword)
            .putString(KEY_RECIPIENT_EMAIL, config.recipientEmail)
            .apply()
    }

    fun loadColorStyle(): String {
        return prefs.getString(KEY_COLOR_STYLE, ThemeStyleManager.STYLE_PURPLE).orEmpty()
    }

    fun saveColorStyle(style: String) {
        prefs.edit()
            .putString(KEY_COLOR_STYLE, style)
            .apply()
    }

    fun loadCustomThemeColor(): Int {
        return prefs.getInt(KEY_CUSTOM_THEME_COLOR, Color.parseColor("#6750A4"))
    }

    fun saveCustomThemeColor(color: Int) {
        prefs.edit()
            .putInt(KEY_CUSTOM_THEME_COLOR, color)
            .apply()
    }

    fun hasSuccessfulBackupToday(): Boolean {
        return prefs.getString(KEY_LAST_SUCCESS_DATE, "") == todayKey()
    }

    fun markBackupSuccessToday() {
        prefs.edit()
            .putString(KEY_LAST_SUCCESS_DATE, todayKey())
            .apply()
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    companion object {
        private const val PREF_NAME = "backup_config"
        private const val KEY_SMTP_HOST = "smtp_host"
        private const val KEY_SMTP_PORT = "smtp_port"
        private const val KEY_SENDER_EMAIL = "sender_email"
        private const val KEY_SENDER_PASSWORD = "sender_password"
        private const val KEY_RECIPIENT_EMAIL = "recipient_email"
        private const val KEY_LAST_SUCCESS_DATE = "last_success_date"
        private const val KEY_COLOR_STYLE = "color_style"
        private const val KEY_CUSTOM_THEME_COLOR = "custom_theme_color"
    }
}
