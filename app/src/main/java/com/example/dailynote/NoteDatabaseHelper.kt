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
        db.execSQL(
            """
            CREATE TABLE $TABLE_NOTES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NOTES")
        onCreate(db)
    }

    fun addNote(content: String) {
        val values = ContentValues().apply {
            put(COL_CONTENT, content)
            put(COL_CREATED_AT, System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE_NOTES, null, values)
    }

    fun updateNote(id: Long, content: String) {
        val values = ContentValues().apply {
            put(COL_CONTENT, content)
        }
        writableDatabase.update(TABLE_NOTES, values, "$COL_ID=?", arrayOf(id.toString()))
    }

    fun deleteNote(id: Long) {
        writableDatabase.delete(TABLE_NOTES, "$COL_ID=?", arrayOf(id.toString()))
    }

    fun getNotesGroupedByDay(dayLimit: Int? = null): Map<String, List<Note>> {
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

    companion object {
        const val DATABASE_NAME = "daily_note.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_NOTES = "notes"
        const val COL_ID = "id"
        const val COL_CONTENT = "content"
        const val COL_CREATED_AT = "created_at"
    }
}


data class NoteSummaryStats(
    val totalDays: Int = 0,
    val totalNotes: Int = 0,
    val totalChars: Int = 0
)
