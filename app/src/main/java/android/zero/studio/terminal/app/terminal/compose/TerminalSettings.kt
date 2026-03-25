package android.zero.studio.terminal.app.terminal.compose

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 终端设置状态管理器。
 * 提供实时的 StateFlow 以便 Compose 和 TerminalView 立即响应设置变化。
 * @author android_zero
 */
object TerminalSettings {
    private const val PREFS_NAME = "terminal_settings_prefs"
    private lateinit var prefs: SharedPreferences

    val fontSize = MutableStateFlow(12)
    val keepScreenOn = MutableStateFlow(false)
    val cursorStyle = MutableStateFlow(0) // 0: Block, 1: Underline, 2: Bar
    val bellVibrate = MutableStateFlow(true)
    val projectAsPwd = MutableStateFlow(false)

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fontSize.value = prefs.getInt("font_size", 12)
        keepScreenOn.value = prefs.getBoolean("keep_screen_on", false)
        cursorStyle.value = prefs.getInt("cursor_style", 0)
        bellVibrate.value = prefs.getBoolean("bell_vibrate", true)
        projectAsPwd.value = prefs.getBoolean("project_as_pwd", false)
    }

    fun updateFontSize(size: Int) {
        fontSize.value = size
        prefs.edit().putInt("font_size", size).apply()
    }

    fun updateKeepScreenOn(keep: Boolean) {
        keepScreenOn.value = keep
        prefs.edit().putBoolean("keep_screen_on", keep).apply()
    }

    fun updateCursorStyle(style: Int) {
        cursorStyle.value = style
        prefs.edit().putInt("cursor_style", style).apply()
    }

    fun updateBellVibrate(vibrate: Boolean) {
        bellVibrate.value = vibrate
        prefs.edit().putBoolean("bell_vibrate", vibrate).apply()
    }
}