package android.zero.studio.terminal.app.terminal.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment

/**
 * 现代化终端设置页面 Fragment。
 * @author android_zero
 */
class TerminalSettingsFragment : Fragment() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Terminal Settings") },
                                navigationIcon = {
                                    IconButton(onClick = { parentFragmentManager.popBackStack() }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    ) { padding ->
                        SettingsContent(Modifier.padding(padding))
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsContent(modifier: Modifier) {
        val fontSize by TerminalSettings.fontSize.collectAsState()
        val keepScreenOn by TerminalSettings.keepScreenOn.collectAsState()
        val cursorStyle by TerminalSettings.cursorStyle.collectAsState()
        val bellVibrate by TerminalSettings.bellVibrate.collectAsState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            
            // 字体大小
            Text("Font Size: $fontSize pt")
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { TerminalSettings.updateFontSize(it.toInt()) },
                valueRange = 8f..32f,
                steps = 24
            )
            Divider(Modifier.padding(vertical = 8.dp))

            // 屏幕常亮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Keep Screen On")
                    Text("Prevent screen from sleeping in terminal", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Switch(checked = keepScreenOn, onCheckedChange = { TerminalSettings.updateKeepScreenOn(it) })
            }
            Divider(Modifier.padding(vertical = 8.dp))

            // 响铃震动反馈
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Bell Vibration")
                    Text("Vibrate on terminal bell (Ctrl+G)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Switch(checked = bellVibrate, onCheckedChange = { TerminalSettings.updateBellVibrate(it) })
            }
            Divider(Modifier.padding(vertical = 8.dp))

            // 光标样式
            Text("Cursor Style", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            val cursorOptions = listOf("Block", "Underline", "Bar")
            cursorOptions.forEachIndexed { index, name ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(
                        selected = cursorStyle == index,
                        onClick = { TerminalSettings.updateCursorStyle(index) }
                    )
                    Text(name, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}