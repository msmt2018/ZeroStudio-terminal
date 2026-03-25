package android.zero.studio.terminal.app.terminal.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import android.zero.studio.terminal.app.terminal.compose.*

/**
 * 承载终端功能，完全使用 Compose + Material3。
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
                    Surface(modifier = Modifier.fillMaxSize()) {
                        when (val state = viewModel.state) {
                            is TerminalState.Checking -> LoadingScreen("Checking Ubuntu Environment...")
                            is TerminalState.Downloading -> DownloadingScreen(state)
                            is TerminalState.SettingUp -> SetupScreen(state.setupSession)
                            is TerminalState.Ready -> TerminalScreen(viewModel)
                            is TerminalState.Error -> ErrorScreen(state.message) { viewModel.checkAndSetup(requireContext()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message)
    }
}

@Composable
fun DownloadingScreen(state: TerminalState.Downloading) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Downloading ${state.fileName}...")
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(Modifier.height(8.dp))
        Text("${state.downloadedMb} MB / ${state.totalMb} MB")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(session: android.zero.studio.terminal.terminal.TerminalSession) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Extracting RootFS...") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            TerminalWorkspace(session)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Error: $message", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}