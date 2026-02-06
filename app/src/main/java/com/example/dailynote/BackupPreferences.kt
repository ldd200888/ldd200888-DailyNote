package com.example.dailynote

import android.content.Context
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupPreferences(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val dbHelper by lazy { NoteDatabaseHelper(context.applicationContext) }

    fun loadConfig(): BackupConfig {
        return BackupConfig(
            smtpHost = loadString(KEY_SMTP_HOST, "smtp.163.com"),
            smtpPort = loadInt(KEY_SMTP_PORT, 465),
            senderEmail = loadString(KEY_SENDER_EMAIL, ""),
            senderPassword = loadString(KEY_SENDER_PASSWORD, ""),
            recipientEmail = loadString(KEY_RECIPIENT_EMAIL, "")
        )
    }

    fun saveConfig(config: BackupConfig) {
        saveString(KEY_SMTP_HOST, config.smtpHost)
        saveInt(KEY_SMTP_PORT, config.smtpPort)
        saveString(KEY_SENDER_EMAIL, config.senderEmail)
        saveString(KEY_SENDER_PASSWORD, config.senderPassword)
        saveString(KEY_RECIPIENT_EMAIL, config.recipientEmail)
    }

    fun loadColorStyle(): String {
        return loadString(KEY_COLOR_STYLE, ThemeStyleManager.STYLE_PURPLE)
    }

    fun saveColorStyle(style: String) {
        saveString(KEY_COLOR_STYLE, style)
    }

    fun loadCustomThemeColor(): Int {
        return loadInt(KEY_CUSTOM_THEME_COLOR, Color.parseColor("#6750A4"))
    }

    fun saveCustomThemeColor(color: Int) {
        saveInt(KEY_CUSTOM_THEME_COLOR, color)
    }

    fun isBiometricLockEnabled(): Boolean {
        return loadBoolean(KEY_BIOMETRIC_LOCK_ENABLED, false)
    }

    fun loadExpandMode(): Int {
        return loadInt(KEY_EXPAND_MODE, EXPAND_MODE_LATEST_DAY)
    }

    fun saveExpandMode(mode: Int) {
        saveInt(KEY_EXPAND_MODE, mode)
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        saveBoolean(KEY_BIOMETRIC_LOCK_ENABLED, enabled)
    }

    fun hasSuccessfulEmailBackupToday(): Boolean {
        return loadString(KEY_LAST_EMAIL_SUCCESS_DATE, "") == todayKey()
    }

    fun markEmailBackupSuccessToday() {
        saveString(KEY_LAST_EMAIL_SUCCESS_DATE, todayKey())
    }

    fun hasSuccessfulLocalBackupToday(): Boolean {
        return loadString(KEY_LAST_LOCAL_SUCCESS_DATE, "") == todayKey()
    }

    fun markLocalBackupSuccessToday() {
        saveString(KEY_LAST_LOCAL_SUCCESS_DATE, todayKey())
    }

    fun isFirstLaunchAfterInstall(): Boolean {
        return loadBoolean(KEY_IS_FIRST_LAUNCH_AFTER_INSTALL, true)
    }

    fun markFirstLaunchHandled() {
        saveBoolean(KEY_IS_FIRST_LAUNCH_AFTER_INSTALL, false)
    }

    private fun loadString(key: String, defaultValue: String): String {
        val dbValue = dbHelper.getSetting(key)
        if (dbValue != null) {
            prefs.edit().putString(key, dbValue).apply()
            return dbValue
        }

        val value = prefs.getString(key, defaultValue).orEmpty()
        dbHelper.putSetting(key, value)
        return value
    }

    private fun loadInt(key: String, defaultValue: Int): Int {
        val dbValue = dbHelper.getSetting(key)?.toIntOrNull()
        if (dbValue != null) {
            prefs.edit().putInt(key, dbValue).apply()
            return dbValue
        }

        val value = prefs.getInt(key, defaultValue)
        dbHelper.putSetting(key, value.toString())
        return value
    }

    private fun loadBoolean(key: String, defaultValue: Boolean): Boolean {
        val dbValue = dbHelper.getSetting(key)?.toBooleanStrictOrNull()
        if (dbValue != null) {
            prefs.edit().putBoolean(key, dbValue).apply()
            return dbValue
        }

        val value = prefs.getBoolean(key, defaultValue)
        dbHelper.putSetting(key, value.toString())
        return value
    }

    private fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        dbHelper.putSetting(key, value)
    }

    private fun saveInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        dbHelper.putSetting(key, value.toString())
    }

    private fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        dbHelper.putSetting(key, value.toString())
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
