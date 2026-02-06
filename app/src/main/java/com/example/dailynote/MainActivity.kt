package com.example.dailynote

import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
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
    private lateinit var seekDayNavigator: SeekBar
    private lateinit var recyclerNotes: RecyclerView
    private var visibleDayCount = DEFAULT_VISIBLE_DAYS
    private var expandMode = BackupPreferences.EXPAND_MODE_LATEST_DAY
    private var isSyncingDaySeekBar = false
    private var currentColorStyle = ThemeStyleManager.STYLE_PURPLE
    private var currentCustomColor = 0
    private var hasAuthenticated = false
    private var isBiometricLockEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeStyleManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ThemeStyleManager.applyCustomColorIfNeeded(this)

        tryRestoreOnFirstLaunch()

        dbHelper = NoteDatabaseHelper(this)
        adapter = NoteAdapter(onNoteLongClick = ::showNoteActionDialog)

        editNote = findViewById(R.id.editNote)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnInfo = findViewById<ImageButton>(R.id.btnInfo)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        btnLoadMore = findViewById(R.id.btnLoadMore)
        seekDayNavigator = findViewById(R.id.seekDayNavigator)
        recyclerNotes = findViewById(R.id.recyclerNotes)

        recyclerNotes.layoutManager = LinearLayoutManager(this)
        recyclerNotes.adapter = adapter
        setupDaySeekBar()

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

        btnInfo.setOnClickListener {
            showAppInfoDialog()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val prefs = BackupPreferences(this)
        currentColorStyle = prefs.loadColorStyle()
        currentCustomColor = prefs.loadCustomThemeColor()
        isBiometricLockEnabled = prefs.isBiometricLockEnabled()
        expandMode = prefs.loadExpandMode()

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

        expandMode = prefs.loadExpandMode()
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


    private fun tryRestoreOnFirstLaunch() {
        val prefs = BackupPreferences(this)
        if (!prefs.isFirstLaunchAfterInstall()) {
            return
        }

        runCatching {
            LocalBackupManager(applicationContext).restoreLatestBackupIfExists()
        }.onSuccess { restored ->
            if (restored) {
                Toast.makeText(this, "检测到本地备份，已自动恢复", Toast.LENGTH_SHORT).show()
                prefs.markFirstLaunchHandled()
            } else {
                Toast.makeText(this, "未找到可恢复的本地备份", Toast.LENGTH_SHORT).show()
            }
        }.onFailure { throwable ->
            Log.e(TAG, "首次启动自动恢复失败", throwable)
            Toast.makeText(this, "自动恢复失败，请检查备份文件", Toast.LENGTH_SHORT).show()
        }
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
                        prefs.markLocalBackupFailure(it.message ?: "未知原因")
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
                        prefs.markEmailBackupFailure(it.message ?: "未知原因")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "今日邮箱备份失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                tasks.forEach { it.await() }
            }
        }
    }


    private fun showAppInfoDialog() {
        val stats = dbHelper.getSummaryStats()
        val prefs = BackupPreferences(this)
        val localBackupStatus = if (prefs.hasSuccessfulLocalBackupToday()) "成功（今日）" else "未成功（今日）"
        val emailBackupStatus = if (prefs.hasSuccessfulEmailBackupToday()) "成功（今日）" else "未成功（今日）"
        val localBackupFailureReason = prefs.lastLocalBackupFailureReason().ifBlank { "暂无" }
        val emailBackupFailureReason = prefs.lastEmailBackupFailureReason().ifBlank { "暂无" }

        val message = buildString {
            appendLine("记录总天数：${stats.totalDays} 天")
            appendLine("记录总条数：${stats.totalNotes} 条")
            appendLine("总字数：${stats.totalChars} 字")
            appendLine("本地备份是否成功：$localBackupStatus")
            appendLine("邮箱备份是否成功：$emailBackupStatus")
            appendLine("本地备份失败原因：$localBackupFailureReason")
            append("邮箱备份失败原因：$emailBackupFailureReason")
        }

        AlertDialog.Builder(this)
            .setTitle("程序状态信息")
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun setupDaySeekBar() {
        adapter.setHeaderPositionListener(object : NoteAdapter.HeaderPositionListener {
            override fun onHeaderPositionsChanged(dayHeaders: List<String>) {
                isSyncingDaySeekBar = true
                seekDayNavigator.max = if (dayHeaders.isEmpty()) 0 else dayHeaders.lastIndex
                seekDayNavigator.progress = 0
                seekDayNavigator.visibility = if (dayHeaders.size > 1) View.VISIBLE else View.GONE
                isSyncingDaySeekBar = false
            }
        })

        seekDayNavigator.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isSyncingDaySeekBar) {
                    return
                }
                val days = adapter.getDayHeaders()
                if (days.isEmpty()) {
                    return
                }
                val safeIndex = progress.coerceIn(0, days.lastIndex)
                val targetDay = days[safeIndex]
                val position = adapter.getHeaderAdapterPosition(targetDay)
                if (position >= 0) {
                    recyclerNotes.scrollToPosition(position)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
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
        adapter.submit(grouped, expandMode)

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
        private const val TAG = "MainActivity"
    }
}
