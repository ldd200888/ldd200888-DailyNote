package com.example.dailynote

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeStyleManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = BackupPreferences(this)
        val current = prefs.loadConfig()

        val editSmtpHost = findViewById<EditText>(R.id.editSmtpHost)
        val editSmtpPort = findViewById<EditText>(R.id.editSmtpPort)
        val editSenderEmail = findViewById<EditText>(R.id.editSenderEmail)
        val editSenderPassword = findViewById<EditText>(R.id.editSenderPassword)
        val editRecipientEmail = findViewById<EditText>(R.id.editRecipientEmail)
        val radioGroupColorStyle = findViewById<RadioGroup>(R.id.radioGroupColorStyle)
        val btnSave = findViewById<Button>(R.id.btnSaveConfig)

        editSmtpHost.setText(current.smtpHost)
        editSmtpPort.setText(current.smtpPort.toString())
        editSenderEmail.setText(current.senderEmail)
        editSenderPassword.setText(current.senderPassword)
        editRecipientEmail.setText(current.recipientEmail)

        when (prefs.loadColorStyle()) {
            ThemeStyleManager.STYLE_BLUE -> radioGroupColorStyle.check(R.id.radioStyleBlue)
            ThemeStyleManager.STYLE_GREEN -> radioGroupColorStyle.check(R.id.radioStyleGreen)
            ThemeStyleManager.STYLE_ORANGE -> radioGroupColorStyle.check(R.id.radioStyleOrange)
            else -> radioGroupColorStyle.check(R.id.radioStylePurple)
        }

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
                else -> ThemeStyleManager.STYLE_PURPLE
            }

            prefs.saveConfig(config)
            prefs.saveColorStyle(selectedStyle)
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
