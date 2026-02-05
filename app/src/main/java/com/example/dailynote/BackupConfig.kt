package com.example.dailynote

data class BackupConfig(
    val smtpHost: String,
    val smtpPort: Int,
    val senderEmail: String,
    val senderPassword: String,
    val recipientEmail: String
)
