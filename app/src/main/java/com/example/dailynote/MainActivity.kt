package com.example.dailynote

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: NoteDatabaseHelper
    private lateinit var adapter: NoteAdapter

    private lateinit var editNote: EditText
    private lateinit var btnLoadMore: Button
    private var visibleDayCount = DEFAULT_VISIBLE_DAYS
    private var currentColorStyle = ThemeStyleManager.STYLE_PURPLE
    private var currentCustomColor = 0
    private var hasAuthenticated = false
    private var isBiometricLockEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeStyleManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ThemeStyleManager.applyCustomColorIfNeeded(this)

        dbHelper = NoteDatabaseHelper(this)
        adapter = NoteAdapter(onNoteLongClick = ::showNoteActionDialog)

        editNote = findViewById(R.id.editNote)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        btnLoadMore = findViewById(R.id.btnLoadMore)
        val recyclerNotes = findViewById<RecyclerView>(R.id.recyclerNotes)

        recyclerNotes.layoutManager = LinearLayoutManager(this)
        recyclerNotes.adapter = adapter

        btnSave.setOnClickListener {
            val text = editNote.text.toString().trim()
            if (text.isBlank()) {
                Toast.makeText(this, "请输入记事内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dbHelper.addNote(text)
            editNote.text?.clear()
            visibleDayCount = DEFAULT_VISIBLE_DAYS
            loadNotes()
        }

        btnLoadMore.setOnClickListener {
            visibleDayCount += DEFAULT_VISIBLE_DAYS
            loadNotes()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val prefs = BackupPreferences(this)
        currentColorStyle = prefs.loadColorStyle()
        currentCustomColor = prefs.loadCustomThemeColor()
        isBiometricLockEnabled = prefs.isBiometricLockEnabled()

        if (isBiometricLockEnabled) {
            authenticateWithBiometric()
        } else {
            hasAuthenticated = true
            tryBackupOnOpen()
            loadNotes()
            focusInputAndShowKeyboard()
        }
    }

    override fun onResume() {
        super.onResume()

        val prefs = BackupPreferences(this)
        val latestStyle = prefs.loadColorStyle()
        val latestCustomColor = prefs.loadCustomThemeColor()
        val latestBiometricLockEnabled = prefs.isBiometricLockEnabled()
        if (latestStyle != currentColorStyle ||
            (latestStyle == ThemeStyleManager.STYLE_CUSTOM && latestCustomColor != currentCustomColor)
        ) {
            recreate()
            return
        }

        if (latestBiometricLockEnabled != isBiometricLockEnabled) {
            isBiometricLockEnabled = latestBiometricLockEnabled
            hasAuthenticated = !isBiometricLockEnabled
            if (isBiometricLockEnabled) {
                authenticateWithBiometric()
            }
            return
        }

        if (hasAuthenticated) {
            loadNotes()
        }
    }

    private fun authenticateWithBiometric() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        val biometricManager = BiometricManager.from(this)

        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "设备未设置指纹/生物识别，无法进入", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    hasAuthenticated = true
                    tryBackupOnOpen()
                    loadNotes()
                    focusInputAndShowKeyboard()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@MainActivity, "认证失败，应用将退出", Toast.LENGTH_SHORT).show()
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, "指纹不匹配，请重试", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("身份验证")
            .setSubtitle("请使用指纹进入日记")
            .setNegativeButtonText("取消")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun tryBackupOnOpen() {
        val prefs = BackupPreferences(this)
        val config = prefs.loadConfig()
        val canDoEmailBackup = config.senderEmail.isNotBlank() &&
            config.senderPassword.isNotBlank() &&
            config.recipientEmail.isNotBlank()

        val shouldDoLocalBackup = !prefs.hasSuccessfulLocalBackupToday()
        val shouldDoEmailBackup = canDoEmailBackup && !prefs.hasSuccessfulEmailBackupToday()

        if (!shouldDoLocalBackup && !shouldDoEmailBackup) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            supervisorScope {
                val tasks = mutableListOf(async {
                    if (!shouldDoLocalBackup) return@async

                    runCatching {
                        LocalBackupManager(applicationContext).backupDatabase() ?: error("数据库文件不存在")
                    }.onSuccess {
                        prefs.markLocalBackupSuccessToday()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "今日本地备份成功", Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "今日本地备份失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                })

                tasks += async {
                    if (!shouldDoEmailBackup) return@async

                    runCatching {
                        EmailBackupSender(applicationContext).sendDatabaseBackup(config)
                    }.onSuccess {
                        prefs.markEmailBackupSuccessToday()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "今日邮箱备份成功", Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "今日邮箱备份失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                tasks.forEach { it.await() }
            }
        }
    }

    private fun showNoteActionDialog(note: Note) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_note_actions, null)
        dialog.setContentView(view)

        val btnEdit = view.findViewById<Button>(R.id.btnEdit)
        val btnDelete = view.findViewById<Button>(R.id.btnDelete)

        btnEdit.setOnClickListener {
            dialog.dismiss()
            showEditDialog(note)
        }
        btnDelete.setOnClickListener {
            dialog.dismiss()
            dbHelper.deleteNote(note.id)
            loadNotes()
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showEditDialog(note: Note) {
        val input = EditText(this).apply {
            setText(note.content)
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("修改记事")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val updated = input.text.toString().trim()
                if (updated.isBlank()) {
                    Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                dbHelper.updateNote(note.id, updated)
                loadNotes()
                Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadNotes() {
        val grouped = dbHelper.getNotesGroupedByDay(visibleDayCount)
        adapter.submit(grouped)

        val allDayCount = dbHelper.getNotesGroupedByDay().size
        btnLoadMore.visibility = if (allDayCount > visibleDayCount) View.VISIBLE else View.GONE
    }

    private fun focusInputAndShowKeyboard() {
        editNote.requestFocus()
        editNote.post {
            val inputMethodManager = getSystemService(InputMethodManager::class.java)
            inputMethodManager?.showSoftInput(editNote, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    companion object {
        private const val DEFAULT_VISIBLE_DAYS = 5
    }
}
