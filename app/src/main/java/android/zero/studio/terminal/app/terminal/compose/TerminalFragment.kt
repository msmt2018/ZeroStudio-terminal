package android.zero.studio.terminal.app.terminal.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels

/**
 * Terminal UI 容器。
 * 添加双指缩放、剪切板、上下文菜单、虚拟键盘及沉浸式问题。
 * @author android_zero
 */
class TerminalFragment : Fragment() {

    private val viewModel: TerminalViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (viewModel.state is TerminalState.Checking) {
            viewModel.checkAndSetup(requireContext())
        }

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                        when (val state = viewModel.state) {
                            is TerminalState.Checking -> LoadingScreen("Checking Ubuntu Environment...")
                            is TerminalState.Downloading -> DownloadingScreen(state)
                            is TerminalState.SettingUp -> SetupScreen(state.setupSession)
                            is TerminalState.Ready -> TerminalScreen(viewModel, this@TerminalFragment)
                            is TerminalState.Error -> ErrorScreen(state.message) { viewModel.checkAndSetup(requireContext()) }
                        }
                    }
                }
            }
        }
    }
}