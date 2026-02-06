package com.example.dailynote

import android.content.Context
import java.io.File
import net.sqlcipher.database.SQLiteDatabase

object BackupSqlCipher {
    fun exportEncryptedBackup(context: Context, sourceDbFile: File, outputFile: File, password: String) {
        SQLiteDatabase.loadLibs(context)
        val db = SQLiteDatabase.openDatabase(
            sourceDbFile.absolutePath,
            "",
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            db.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf(outputFile.absolutePath, password))
            db.execSQL("SELECT sqlcipher_export('encrypted')")
            db.execSQL("DETACH DATABASE encrypted")
        } finally {
            db.close()
        }
    }

    fun exportPlaintextDatabase(context: Context, encryptedDbFile: File, outputFile: File, password: String) {
        SQLiteDatabase.loadLibs(context)
        val db = SQLiteDatabase.openDatabase(
            encryptedDbFile.absolutePath,
            password,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            db.execSQL("ATTACH DATABASE ? AS plaintext KEY ''", arrayOf(outputFile.absolutePath))
            db.execSQL("PRAGMA plaintext.cipher_plaintext_header_size = 32")
            db.execSQL("SELECT sqlcipher_export('plaintext')")
            db.execSQL("DETACH DATABASE plaintext")
        } finally {
            db.close()
        }
    }
}
