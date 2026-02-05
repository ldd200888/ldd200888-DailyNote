package com.example.dailynote

import androidx.appcompat.app.AppCompatActivity

object ThemeStyleManager {

    const val STYLE_PURPLE = "purple"
    const val STYLE_BLUE = "blue"
    const val STYLE_GREEN = "green"
    const val STYLE_ORANGE = "orange"

    fun apply(activity: AppCompatActivity) {
        activity.setTheme(resolveThemeResId(BackupPreferences(activity).loadColorStyle()))
    }

    fun resolveThemeResId(style: String): Int {
        return when (style) {
            STYLE_BLUE -> R.style.Theme_DailyNote_Blue
            STYLE_GREEN -> R.style.Theme_DailyNote_Green
            STYLE_ORANGE -> R.style.Theme_DailyNote_Orange
            else -> R.style.Theme_DailyNote
        }
    }
}
