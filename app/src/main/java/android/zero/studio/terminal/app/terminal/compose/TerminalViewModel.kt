package android.zero.studio.terminal.app.terminal.compose

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.zero.studio.terminal.app.terminal.proot.PRootEnvironment
import android.zero.studio.terminal.shared.termux.terminal.TermuxTerminalSessionClientBase
import android.zero.studio.terminal.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch

sealed class TerminalState {
    object Checking : TerminalState()
    data class Downloading(val fileName: String, val progress: Float, val downloadedMb: String, val totalMb: String) : TerminalState()
    data class SettingUp(val setupSession: TerminalSession) : TerminalState()
    object Ready : TerminalState()
    data class Error(val message: String) : TerminalState()
}

/**
 * 控制 Terminal 状态逻辑、会话创建与分发
 * @author android_zero
 */
class TerminalViewModel : ViewModel() {
    var state by mutableStateOf<TerminalState>(TerminalState.Checking)
    val sessions = mutableStateListOf<TerminalSession>()
    var currentIndex by mutableIntStateOf(0)

    fun checkAndSetup(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { state = TerminalState.Checking }

                if (PRootEnvironment.isEnvironmentInstalled(context)) {
                    withContext(Dispatchers.Main) {
                        state = TerminalState.Ready
                        if (sessions.isEmpty()) addSession(context)
                    }
                    return@launch
                }

                PRootEnvironment.extractAssets(context)

                val binDir = PRootEnvironment.localBinDir(context)
                val libDir = PRootEnvironment.localLibDir(context)
                val cacheDir = context.cacheDir // 直接下载到 cacheDir 匹配 Xed 的 getTempDir()

                val filesToDownload = mutableListOf<Pair<String, File>>()
                filesToDownload.add(PRootEnvironment.getProotUrl() to File(binDir, "proot"))
                filesToDownload.add(PRootEnvironment.getTallocUrl() to File(libDir, "libtalloc.so.2"))

                if (!PRootEnvironment.isEnvironmentInstalled(context)) {
                    filesToDownload.add(PRootEnvironment.getRootfsUrl() to File(cacheDir, "sandbox.tar.gz"))
                }

                for ((url, file) in filesToDownload) {
                    if (!file.exists()) {
                        PRootEnvironment.downloadFileWithRetry(url, file) { downloaded, total ->
                            val progress = if (total > 0) downloaded.toFloat() / total else 0f
                            val downloadedMb = String.format("%.2f", downloaded / (1024.0 * 1024.0))
                            val totalMb = String.format("%.2f", total / (1024.0 * 1024.0))
                            state = TerminalState.Downloading(file.name, progress, downloadedMb, totalMb)
                        }
                        if (file.name == "proot") file.setExecutable(true)
                    }
                }

                val setupLatch = CountDownLatch(1)
                var setupExitCode = -1
                val client = object : TermuxTerminalSessionClientBase() {
                    override fun onSessionFinished(finishedSession: TerminalSession) {
                        setupExitCode = finishedSession.exitStatus
                        setupLatch.countDown()
                    }
                }

                // 强制在主线程构建 Session 防止 Handler 崩溃
                val setupSession = withContext(Dispatchers.Main) {
                    PRootEnvironment.createSetupSession(context, client)
                }

                withContext(Dispatchers.Main) {
                    state = TerminalState.SettingUp(setupSession)
                }

                setupLatch.await()

                if (setupExitCode == 0) {
                    PRootEnvironment.markEnvironmentInstalled(context)
                    withContext(Dispatchers.Main) {
                        state = TerminalState.Ready
                        addSession(context)
                    }
                } else {
                    withContext(Dispatchers.Main) { state = TerminalState.Error("Setup failed with exit code $setupExitCode") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { state = TerminalState.Error(e.message ?: "Unknown error") }
            }
        }
    }

    fun addSession(context: Context) {
        val sessionId = "Session ${sessions.size + 1}"
        val client = object : TermuxTerminalSessionClientBase() {
            override fun onSessionFinished(finishedSession: TerminalSession) {
                Handler(Looper.getMainLooper()).post {
                    val index = sessions.indexOf(finishedSession)
                    if (index != -1) {
                        sessions.removeAt(index)
                        if (currentIndex >= sessions.size) {
                            currentIndex = maxOf(0, sessions.size - 1)
                        }
                    }
                }
            }
        }
        val session = PRootEnvironment.createSession(context, sessionId, client)
        sessions.add(session)
        currentIndex = sessions.size - 1
    }

    fun closeSession(index: Int) {
        if (index in sessions.indices) {
            val session = sessions[index]
            session.finishIfRunning()
            sessions.removeAt(index)
            if (currentIndex >= sessions.size) {
                currentIndex = maxOf(0, sessions.size - 1)
            }
        }
    }
}