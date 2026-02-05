package com.example.dailynote

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = BackupPreferences(this)
        val current = prefs.loadConfig()

        val editSmtpHost = findViewById<EditText>(R.id.editSmtpHost)
        val editSmtpPort = findViewById<EditText>(R.id.editSmtpPort)
        val editSenderEmail = findViewById<EditText>(R.id.editSenderEmail)
        val editSenderPassword = findViewById<EditText>(R.id.editSenderPassword)
        val editRecipientEmail = findViewById<EditText>(R.id.editRecipientEmail)
        val editHour = findViewById<EditText>(R.id.editBackupHour)
        val editMinute = findViewById<EditText>(R.id.editBackupMinute)
        val btnSave = findViewById<Button>(R.id.btnSaveConfig)

        editSmtpHost.setText(current.smtpHost)
        editSmtpPort.setText(current.smtpPort.toString())
        editSenderEmail.setText(current.senderEmail)
        editSenderPassword.setText(current.senderPassword)
        editRecipientEmail.setText(current.recipientEmail)
        editHour.setText(current.backupHour.toString())
        editMinute.setText(current.backupMinute.toString())

        btnSave.setOnClickListener {
            val hour = editHour.text.toString().toIntOrNull()
            val minute = editMinute.text.toString().toIntOrNull()
            val port = editSmtpPort.text.toString().toIntOrNull()

            if (hour == null || minute == null || port == null || hour !in 0..23 || minute !in 0..59) {
                Toast.makeText(this, "请输入正确的端口和时间", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val config = BackupConfig(
                smtpHost = editSmtpHost.text.toString().trim(),
                smtpPort = port,
                senderEmail = editSenderEmail.text.toString().trim(),
                senderPassword = editSenderPassword.text.toString().trim(),
                recipientEmail = editRecipientEmail.text.toString().trim(),
                backupHour = hour,
                backupMinute = minute
            )

            prefs.saveConfig(config)
            BackupScheduler.schedule(this, config.backupHour, config.backupMinute)
            Toast.makeText(this, "备份配置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
