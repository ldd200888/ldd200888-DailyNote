package com.example.dailynote

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity

object ThemeStyleManager {

    const val STYLE_PURPLE = "purple"
    const val STYLE_BLUE = "blue"
    const val STYLE_GREEN = "green"
    const val STYLE_ORANGE = "orange"
    const val STYLE_CUSTOM = "custom"

    fun apply(activity: AppCompatActivity) {
        activity.setTheme(resolveThemeResId(BackupPreferences(activity).loadColorStyle()))
    }

    fun applyCustomColorIfNeeded(activity: AppCompatActivity) {
        val prefs = BackupPreferences(activity)
        if (prefs.loadColorStyle() != STYLE_CUSTOM) {
            return
        }

        val color = prefs.loadCustomThemeColor()
        val colorStateList = ColorStateList.valueOf(color)
        val actionBar = activity.supportActionBar

        applyActionBarColor(actionBar, color)
        activity.window.statusBarColor = darken(color)
        activity.window.navigationBarColor = darken(color)

        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        tintInteractiveViews(root, colorStateList, color)
    }

    fun resolveThemeResId(style: String): Int {
        return when (style) {
            STYLE_BLUE -> R.style.Theme_DailyNote_Blue
            STYLE_GREEN -> R.style.Theme_DailyNote_Green
            STYLE_ORANGE -> R.style.Theme_DailyNote_Orange
            STYLE_CUSTOM -> R.style.Theme_DailyNote
            else -> R.style.Theme_DailyNote
        }
    }

    private fun applyActionBarColor(actionBar: ActionBar?, color: Int) {
        actionBar ?: return
        actionBar.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color))
    }

    private fun tintInteractiveViews(view: View, colorStateList: ColorStateList, color: Int) {
        when (view) {
            is Button -> view.backgroundTintList = colorStateList
            is ImageButton -> {
                view.imageTintList = colorStateList
                view.setColorFilter(color)
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                tintInteractiveViews(view.getChildAt(i), colorStateList, color)
            }
        }
    }

    private fun darken(color: Int): Int {
        val factor = 0.85f
        return Color.rgb(
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        )
    }
}
