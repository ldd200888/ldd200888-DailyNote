package com.example.dailynote

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: NoteDatabaseHelper
    private lateinit var adapter: NoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = NoteDatabaseHelper(this)
        adapter = NoteAdapter()

        val editNote = findViewById<EditText>(R.id.editNote)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnSettings = findViewById<Button>(R.id.btnSettings)
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
            loadNotes()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val config = BackupPreferences(this).loadConfig()
        BackupScheduler.schedule(this, config.backupHour, config.backupMinute)
        loadNotes()
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    private fun loadNotes() {
        adapter.submit(dbHelper.getNotesGroupedByDay())
    }
}
