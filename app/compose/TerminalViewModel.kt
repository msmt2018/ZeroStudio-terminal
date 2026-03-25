package android.zero.studio.terminal.app.terminal.compose

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.zero.studio.terminal.app.terminal.proot.PRootEnvironment
import android.zero.studio.terminal.shared.termux.terminal.TermuxTerminalSessionClientBase
import android.zero.studio.terminal.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * 终端 ViewModel。
 * 负责状态流转、服务绑定以及内存安全回收。
 * @author android_zero
 */
class TerminalViewModel : ViewModel() {
    var state by mutableStateOf<TerminalState>(TerminalState.Checking)
    val sessions = mutableStateListOf<TerminalSession>()
    var currentIndex by mutableIntStateOf(0)

    private var terminalService: TerminalSessionService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TerminalSessionService.LocalBinder
            terminalService = binder.getService()
            isBound = true
            
            // 恢复后台会话
            sessions.clear()
            sessions.addAll(terminalService!!.sessions)
            if (sessions.isNotEmpty()) {
                currentIndex = maxOf(0, sessions.size - 1)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            terminalService = null
            isBound = false
        }
    }

    fun checkAndSetup(context: Context) {
        val appContext = context.applicationContext
        
        // 绑定并启动守护服务
        val intent = Intent(appContext, TerminalSessionService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
        appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { state = TerminalState.Checking }

                // 检查是否已经安装
                if (PRootEnvironment.isEnvironmentInstalled(appContext)) {
                    withContext(Dispatchers.Main) { state = TerminalState.Ready }
                    return@launch
                }

                // 释放基础脚本资源
                PRootEnvironment.extractAssets(appContext)

                val binDir = PRootEnvironment.localBinDir(appContext)
                val libDir = PRootEnvironment.localLibDir(appContext)
                val cacheDir = appContext.cacheDir 

                val filesToDownload = mutableListOf<Pair<String, File>>()
                filesToDownload.add(PRootEnvironment.getProotUrl() to File(binDir, "proot"))
                filesToDownload.add(PRootEnvironment.getTallocUrl() to File(libDir, "libtalloc.so.2"))

                if (!PRootEnvironment.isEnvironmentInstalled(appContext)) {
                    filesToDownload.add(PRootEnvironment.getRootfsUrl() to File(cacheDir, "sandbox.tar.gz"))
                }

                // 逐个下载资源
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

                // 使用 Setup Session 执行解压和配置
                val setupLatch = CountDownLatch(1)
                var setupExitCode = -1
                val client = object : TermuxTerminalSessionClientBase() {
                    override fun onSessionFinished(finishedSession: TerminalSession) {
                        setupExitCode = finishedSession.exitStatus
                        setupLatch.countDown()
                    }
                }

                val setupSession = withContext(Dispatchers.Main) {
                    PRootEnvironment.createSetupSession(appContext, client)
                }

                withContext(Dispatchers.Main) {
                    state = TerminalState.SettingUp(setupSession)
                }

                setupLatch.await()

                if (setupExitCode == 0) {
                    PRootEnvironment.markEnvironmentInstalled(appContext)
                    withContext(Dispatchers.Main) { state = TerminalState.Ready }
                } else {
                    withContext(Dispatchers.Main) { state = TerminalState.Error("Setup failed with exit code $setupExitCode") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { state = TerminalState.Error(e.message ?: "Unknown error") }
            }
        }
    }

    /**
     * 添加新会话，依托后台 Service 保活
     */
    fun addSession(context: Context) {
        terminalService?.let { service ->
            val sessionId = "Session ${sessions.size + 1}"
            val session = service.createSession(context.applicationContext, sessionId) { finishedSession ->
                Handler(Looper.getMainLooper()).post {
                    sessions.remove(finishedSession)
                    if (currentIndex >= sessions.size) {
                        currentIndex = maxOf(0, sessions.size - 1)
                    }
                }
            }
            sessions.add(session)
            currentIndex = sessions.size - 1
        }
    }

    fun closeSession(index: Int) {
        if (index in sessions.indices) {
            val session = sessions[index]
            terminalService?.killSession(session)
            sessions.removeAt(index)
            currentIndex = maxOf(0, sessions.size - 1)
        }
    }

    fun closeAllSessions() {
        terminalService?.killAllSessions()
        sessions.clear()
        currentIndex = 0
    }

    override fun onCleared() {
        super.onCleared()
        // 解绑服务
        terminalService = null
        isBound = false
    }
}