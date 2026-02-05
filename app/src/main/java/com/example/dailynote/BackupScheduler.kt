package com.example.dailynote

import android.content.Context

/**
 * 当前版本不再提供自动备份调度，保留空实现仅用于兼容旧代码。
 */
object BackupScheduler {
    fun cancel(context: Context) {
        // no-op
    }
}
