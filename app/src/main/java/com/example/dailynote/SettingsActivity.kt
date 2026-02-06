package com.example.dailynote

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private val importBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            return@registerForActivityResult
        }
        restoreDatabaseFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeStyleManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ThemeStyleManager.applyCustomColorIfNeeded(this)

        val prefs = BackupPreferences(this)
        val current = prefs.loadConfig()

        val editSmtpHost = findViewById<EditText>(R.id.editSmtpHost)
        val editSmtpPort = findViewById<EditText>(R.id.editSmtpPort)
        val editSenderEmail = findViewById<EditText>(R.id.editSenderEmail)
        val editSenderPassword = findViewById<EditText>(R.id.editSenderPassword)
        val editRecipientEmail = findViewById<EditText>(R.id.editRecipientEmail)
        val radioGroupColorStyle = findViewById<RadioGroup>(R.id.radioGroupColorStyle)
        val radioGroupExpandMode = findViewById<RadioGroup>(R.id.radioGroupExpandMode)
        val seekRed = findViewById<SeekBar>(R.id.seekRed)
        val seekGreen = findViewById<SeekBar>(R.id.seekGreen)
        val seekBlue = findViewById<SeekBar>(R.id.seekBlue)
        val textColorValue = findViewById<TextView>(R.id.textColorValue)
        val viewColorPreview = findViewById<View>(R.id.viewColorPreview)
        val btnSave = findViewById<Button>(R.id.btnSaveConfig)
        val btnImportRestore = findViewById<Button>(R.id.btnImportRestore)
        val textLocalBackupPath = findViewById<TextView>(R.id.textLocalBackupPath)
        val switchBiometricLock = findViewById<SwitchCompat>(R.id.switchBiometricLock)

        editSmtpHost.setText(current.smtpHost)
        editSmtpPort.setText(current.smtpPort.toString())
        editSenderEmail.setText(current.senderEmail)
        editSenderPassword.setText(current.senderPassword)
        editRecipientEmail.setText(current.recipientEmail)
        switchBiometricLock.isChecked = prefs.isBiometricLockEnabled()

        val currentCustomColor = prefs.loadCustomThemeColor()
        seekRed.progress = Color.red(currentCustomColor)
        seekGreen.progress = Color.green(currentCustomColor)
        seekBlue.progress = Color.blue(currentCustomColor)

        val updatePreview = {
            val selectedColor = Color.rgb(seekRed.progress, seekGreen.progress, seekBlue.progress)
            textColorValue.text = "RGB(${seekRed.progress}, ${seekGreen.progress}, ${seekBlue.progress})"
            viewColorPreview.setBackgroundColor(selectedColor)
        }

        val colorSeekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updatePreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

        seekRed.setOnSeekBarChangeListener(colorSeekListener)
        seekGreen.setOnSeekBarChangeListener(colorSeekListener)
        seekBlue.setOnSeekBarChangeListener(colorSeekListener)

        when (prefs.loadColorStyle()) {
            ThemeStyleManager.STYLE_BLUE -> radioGroupColorStyle.check(R.id.radioStyleBlue)
            ThemeStyleManager.STYLE_GREEN -> radioGroupColorStyle.check(R.id.radioStyleGreen)
            ThemeStyleManager.STYLE_ORANGE -> radioGroupColorStyle.check(R.id.radioStyleOrange)
            ThemeStyleManager.STYLE_CUSTOM -> radioGroupColorStyle.check(R.id.radioStyleCustom)
            else -> radioGroupColorStyle.check(R.id.radioStylePurple)
        }

        when (prefs.loadExpandMode()) {
            BackupPreferences.EXPAND_MODE_ALL_EXPANDED -> radioGroupExpandMode.check(R.id.radioExpandAll)
            BackupPreferences.EXPAND_MODE_ALL_COLLAPSED -> radioGroupExpandMode.check(R.id.radioCollapseAll)
            else -> radioGroupExpandMode.check(R.id.radioExpandLatest)
        }

        val updateCustomColorVisibility = {
            val visible = radioGroupColorStyle.checkedRadioButtonId == R.id.radioStyleCustom
            val visibility = if (visible) View.VISIBLE else View.GONE
            findViewById<View>(R.id.layoutCustomColor).visibility = visibility
        }

        radioGroupColorStyle.setOnCheckedChangeListener { _, _ ->
            updateCustomColorVisibility()
        }

        updatePreview()
        updateCustomColorVisibility()

        btnImportRestore.setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }

        val backupPathText = LocalBackupManager(this).publicBackupPathDescription()
        textLocalBackupPath.text = "本地保存文件路径（卸载后仍保留）：$backupPathText"

        btnSave.setOnClickListener {
            val port = editSmtpPort.text.toString().toIntOrNull()
            if (port == null) {
                Toast.makeText(this, "请输入正确的端口", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val config = BackupConfig(
                smtpHost = editSmtpHost.text.toString().trim(),
                smtpPort = port,
                senderEmail = editSenderEmail.text.toString().trim(),
                senderPassword = editSenderPassword.text.toString().trim(),
                recipientEmail = editRecipientEmail.text.toString().trim()
            )

            val selectedStyle = when (radioGroupColorStyle.checkedRadioButtonId) {
                R.id.radioStyleBlue -> ThemeStyleManager.STYLE_BLUE
                R.id.radioStyleGreen -> ThemeStyleManager.STYLE_GREEN
                R.id.radioStyleOrange -> ThemeStyleManager.STYLE_ORANGE
                R.id.radioStyleCustom -> ThemeStyleManager.STYLE_CUSTOM
                else -> ThemeStyleManager.STYLE_PURPLE
            }

            val selectedColor = Color.rgb(seekRed.progress, seekGreen.progress, seekBlue.progress)
            val selectedExpandMode = when (radioGroupExpandMode.checkedRadioButtonId) {
                R.id.radioExpandAll -> BackupPreferences.EXPAND_MODE_ALL_EXPANDED
                R.id.radioCollapseAll -> BackupPreferences.EXPAND_MODE_ALL_COLLAPSED
                else -> BackupPreferences.EXPAND_MODE_LATEST_DAY
            }

            prefs.saveConfig(config)
            prefs.saveColorStyle(selectedStyle)
            prefs.saveCustomThemeColor(selectedColor)
            prefs.setBiometricLockEnabled(switchBiometricLock.isChecked)
            prefs.saveExpandMode(selectedExpandMode)
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun restoreDatabaseFromUri(uri: Uri) {
        val dbFile = getDatabasePath(NoteDatabaseHelper.DATABASE_NAME)
        val tempFile = File(cacheDir, "import_restore.db")

        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("无法读取备份文件")

            if (!tempFile.exists() || tempFile.length() == 0L) {
                error("备份文件为空")
            }

            NoteDatabaseHelper(this).close()
            dbFile.parentFile?.mkdirs()

            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
            File(dbFile.absolutePath + "-journal").delete()

            tempFile.inputStream().use { input ->
                dbFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }.onSuccess {
            Toast.makeText(this, "导入恢复成功，请重启应用查看最新内容", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "导入恢复失败：${it.message}", Toast.LENGTH_LONG).show()
        }

        tempFile.delete()
    }

}
