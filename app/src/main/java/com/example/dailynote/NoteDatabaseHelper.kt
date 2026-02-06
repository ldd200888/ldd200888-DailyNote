package com.example.dailynote

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createNotesTable(db)
        createSettingsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createSettingsTable(db)
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        // 兼容从旧备份恢复出的数据库结构不完整场景，避免读取设置时因缺表闪退。
        createNotesTable(db)
        createSettingsTable(db)
    }

    private fun createNotesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NOTES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createSettingsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SETTINGS (
                setting_key TEXT PRIMARY KEY,
                setting_value TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun ensureTables(db: SQLiteDatabase) {
        createNotesTable(db)
        createSettingsTable(db)
    }

    fun addNote(content: String) {
        ensureTables(writableDatabase)
        val values = ContentValues().apply {
            put(COL_CONTENT, content)
            put(COL_CREATED_AT, System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE_NOTES, null, values)
    }

    fun updateNote(id: Long, content: String) {
        ensureTables(writableDatabase)
        val values = ContentValues().apply {
            put(COL_CONTENT, content)
        }
        writableDatabase.update(TABLE_NOTES, values, "$COL_ID=?", arrayOf(id.toString()))
    }

    fun deleteNote(id: Long) {
        ensureTables(writableDatabase)
        writableDatabase.delete(TABLE_NOTES, "$COL_ID=?", arrayOf(id.toString()))
    }

    fun getNotesGroupedByDay(dayLimit: Int? = null): Map<String, List<Note>> {
        ensureTables(readableDatabase)
        val grouped = linkedMapOf<String, MutableList<Note>>()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        readableDatabase.query(
            TABLE_NOTES,
            arrayOf(COL_ID, COL_CONTENT, COL_CREATED_AT),
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT DESC"
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(COL_ID)
            val contentIndex = cursor.getColumnIndexOrThrow(COL_CONTENT)
            val createdAtIndex = cursor.getColumnIndexOrThrow(COL_CREATED_AT)

            while (cursor.moveToNext()) {
                val note = Note(
                    id = cursor.getLong(idIndex),
                    content = cursor.getString(contentIndex),
                    createdAt = cursor.getLong(createdAtIndex)
                )
                val day = formatter.format(Date(note.createdAt))
                if (grouped[day] == null) {
                    if (dayLimit != null && grouped.size >= dayLimit) {
                        continue
                    }
                    grouped[day] = mutableListOf()
                }
                grouped.getValue(day).add(note)
            }
        }

        return grouped
    }


    fun getSummaryStats(): NoteSummaryStats {
        ensureTables(readableDatabase)
        readableDatabase.rawQuery(
            """
            SELECT
                COUNT(*) AS total_count,
                COUNT(DISTINCT DATE($COL_CREATED_AT / 1000, 'unixepoch', 'localtime')) AS total_days,
                COALESCE(SUM(LENGTH($COL_CONTENT)), 0) AS total_chars
            FROM $TABLE_NOTES
            """.trimIndent(),
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return NoteSummaryStats(
                    totalDays = cursor.getInt(cursor.getColumnIndexOrThrow("total_days")),
                    totalNotes = cursor.getInt(cursor.getColumnIndexOrThrow("total_count")),
                    totalChars = cursor.getInt(cursor.getColumnIndexOrThrow("total_chars"))
                )
            }
        }

        return NoteSummaryStats()
    }

    fun getSetting(key: String): String? {
        ensureTables(readableDatabase)
        readableDatabase.query(
            TABLE_SETTINGS,
            arrayOf(COL_SETTING_VALUE),
            "$COL_SETTING_KEY=?",
            arrayOf(key),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(COL_SETTING_VALUE))
            }
        }
        return null
    }

    fun putSetting(key: String, value: String) {
        ensureTables(writableDatabase)
        val values = ContentValues().apply {
            put(COL_SETTING_KEY, key)
            put(COL_SETTING_VALUE, value)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_SETTINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    companion object {
        const val DATABASE_NAME = "daily_note.db"
        private const val DATABASE_VERSION = 2
        const val TABLE_NOTES = "notes"
        const val TABLE_SETTINGS = "app_settings"
        const val COL_ID = "id"
        const val COL_CONTENT = "content"
        const val COL_CREATED_AT = "created_at"
        const val COL_SETTING_KEY = "setting_key"
        const val COL_SETTING_VALUE = "setting_value"
    }
}


data class NoteSummaryStats(
    val totalDays: Int = 0,
    val totalNotes: Int = 0,
    val totalChars: Int = 0
)
