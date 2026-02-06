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

    fun isBiometricLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_LOCK_ENABLED, false)
    }

    fun loadExpandMode(): Int {
        return prefs.getInt(KEY_EXPAND_MODE, EXPAND_MODE_LATEST_DAY)
    }

    fun saveExpandMode(mode: Int) {
        prefs.edit()
            .putInt(KEY_EXPAND_MODE, mode)
            .apply()
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_LOCK_ENABLED, enabled)
            .apply()
    }

    fun hasSuccessfulEmailBackupToday(): Boolean {
        return prefs.getString(KEY_LAST_EMAIL_SUCCESS_DATE, "") == todayKey()
    }

    fun markEmailBackupSuccessToday() {
        prefs.edit()
            .putString(KEY_LAST_EMAIL_SUCCESS_DATE, todayKey())
            .apply()
    }

    fun hasSuccessfulLocalBackupToday(): Boolean {
        return prefs.getString(KEY_LAST_LOCAL_SUCCESS_DATE, "") == todayKey()
    }

    fun markLocalBackupSuccessToday() {
        prefs.edit()
            .putString(KEY_LAST_LOCAL_SUCCESS_DATE, todayKey())
            .apply()
    }

    fun isFirstLaunchAfterInstall(): Boolean {
        return prefs.getBoolean(KEY_IS_FIRST_LAUNCH_AFTER_INSTALL, true)
    }

    fun markFirstLaunchHandled() {
        prefs.edit()
            .putBoolean(KEY_IS_FIRST_LAUNCH_AFTER_INSTALL, false)
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
        private const val KEY_LAST_EMAIL_SUCCESS_DATE = "last_email_success_date"
        private const val KEY_LAST_LOCAL_SUCCESS_DATE = "last_local_success_date"
        private const val KEY_COLOR_STYLE = "color_style"
        private const val KEY_CUSTOM_THEME_COLOR = "custom_theme_color"
        private const val KEY_BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"
        private const val KEY_EXPAND_MODE = "expand_mode"
        private const val KEY_IS_FIRST_LAUNCH_AFTER_INSTALL = "is_first_launch_after_install"

        const val EXPAND_MODE_LATEST_DAY = 0
        const val EXPAND_MODE_ALL_EXPANDED = 1
        const val EXPAND_MODE_ALL_COLLAPSED = 2
    }
}
