package android.zero.studio.terminal.app.terminal.compose

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * 承载 TerminalFragment 的 Activity。
 * @author android_zero
 */
class TerminalActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 防止与状态栏重叠
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val frameLayout = FrameLayout(this).apply { id = View.generateViewId() }
        setContentView(frameLayout)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(frameLayout.id, TerminalFragment())
                .commit()
        }
    }
}