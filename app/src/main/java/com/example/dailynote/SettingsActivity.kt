package com.example.dailynote

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

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
        val seekRed = findViewById<SeekBar>(R.id.seekRed)
        val seekGreen = findViewById<SeekBar>(R.id.seekGreen)
        val seekBlue = findViewById<SeekBar>(R.id.seekBlue)
        val textColorValue = findViewById<TextView>(R.id.textColorValue)
        val viewColorPreview = findViewById<View>(R.id.viewColorPreview)
        val btnSave = findViewById<Button>(R.id.btnSaveConfig)

        editSmtpHost.setText(current.smtpHost)
        editSmtpPort.setText(current.smtpPort.toString())
        editSenderEmail.setText(current.senderEmail)
        editSenderPassword.setText(current.senderPassword)
        editRecipientEmail.setText(current.recipientEmail)

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

            prefs.saveConfig(config)
            prefs.saveColorStyle(selectedStyle)
            prefs.saveCustomThemeColor(selectedColor)
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
