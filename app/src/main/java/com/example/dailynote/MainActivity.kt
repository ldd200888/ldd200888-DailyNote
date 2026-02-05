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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: NoteDatabaseHelper
    private lateinit var adapter: NoteAdapter

    private lateinit var editNote: EditText
    private lateinit var btnLoadMore: Button
    private var visibleDayCount = DEFAULT_VISIBLE_DAYS
    private var currentColorStyle = ThemeStyleManager.STYLE_PURPLE
    private var currentCustomColor = 0

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

        tryBackupOnOpen()
        loadNotes()
        focusInputAndShowKeyboard()
    }

    override fun onResume() {
        super.onResume()

        val prefs = BackupPreferences(this)
        val latestStyle = prefs.loadColorStyle()
        val latestCustomColor = prefs.loadCustomThemeColor()
        if (latestStyle != currentColorStyle ||
            (latestStyle == ThemeStyleManager.STYLE_CUSTOM && latestCustomColor != currentCustomColor)
        ) {
            recreate()
            return
        }

        loadNotes()
    }

    private fun tryBackupOnOpen() {
        val prefs = BackupPreferences(this)
        val config = prefs.loadConfig()

        if (config.senderEmail.isBlank() || config.senderPassword.isBlank() || config.recipientEmail.isBlank()) {
            return
        }

        if (prefs.hasSuccessfulBackupToday()) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                EmailBackupSender(applicationContext).sendDatabaseBackup(config)
            }.onSuccess {
                prefs.markBackupSuccessToday()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "今日备份成功", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "今日备份失败", Toast.LENGTH_SHORT).show()
                }
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
