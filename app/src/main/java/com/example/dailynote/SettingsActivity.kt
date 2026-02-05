package com.example.dailynote

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

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
        val checkSun = findViewById<CheckBox>(R.id.checkSun)
        val checkMon = findViewById<CheckBox>(R.id.checkMon)
        val checkTue = findViewById<CheckBox>(R.id.checkTue)
        val checkWed = findViewById<CheckBox>(R.id.checkWed)
        val checkThu = findViewById<CheckBox>(R.id.checkThu)
        val checkFri = findViewById<CheckBox>(R.id.checkFri)
        val checkSat = findViewById<CheckBox>(R.id.checkSat)
        val btnSave = findViewById<Button>(R.id.btnSaveConfig)

        val weekdayChecks = mapOf(
            Calendar.SUNDAY to checkSun,
            Calendar.MONDAY to checkMon,
            Calendar.TUESDAY to checkTue,
            Calendar.WEDNESDAY to checkWed,
            Calendar.THURSDAY to checkThu,
            Calendar.FRIDAY to checkFri,
            Calendar.SATURDAY to checkSat
        )

        editSmtpHost.setText(current.smtpHost)
        editSmtpPort.setText(current.smtpPort.toString())
        editSenderEmail.setText(current.senderEmail)
        editSenderPassword.setText(current.senderPassword)
        editRecipientEmail.setText(current.recipientEmail)
        editHour.setText(current.backupHour.toString())
        editMinute.setText(current.backupMinute.toString())
        current.backupWeekdays.forEach { weekday ->
            weekdayChecks[weekday]?.isChecked = true
        }

        btnSave.setOnClickListener {
            val hour = editHour.text.toString().toIntOrNull()
            val minute = editMinute.text.toString().toIntOrNull()
            val port = editSmtpPort.text.toString().toIntOrNull()
            val selectedWeekdays = weekdayChecks
                .filterValues { it.isChecked }
                .keys
                .toSet()

            if (hour == null || minute == null || port == null || hour !in 0..23 || minute !in 0..59) {
                Toast.makeText(this, "请输入正确的端口和时间", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedWeekdays.isEmpty()) {
                Toast.makeText(this, "请至少选择一个备份日", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val config = BackupConfig(
                smtpHost = editSmtpHost.text.toString().trim(),
                smtpPort = port,
                senderEmail = editSenderEmail.text.toString().trim(),
                senderPassword = editSenderPassword.text.toString().trim(),
                recipientEmail = editRecipientEmail.text.toString().trim(),
                backupHour = hour,
                backupMinute = minute,
                backupWeekdays = selectedWeekdays
            )

            prefs.saveConfig(config)
            BackupScheduler.schedule(this, config.backupHour, config.backupMinute)
            Toast.makeText(this, "备份配置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
