package android.zero.studio.terminal.app.terminal.compose

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.zero.studio.terminal.R
import android.zero.studio.terminal.app.terminal.proot.PRootEnvironment
import android.zero.studio.terminal.shared.termux.terminal.TermuxTerminalSessionClientBase
import android.zero.studio.terminal.terminal.TerminalSession
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 终端后台持久化服务。
 * 用于确保 PRoot 会话在 Activity 销毁后仍能在后台运行，提供前台 Notification 守护。
 * 
 * @author android_zero
 */
class TerminalSessionService : Service() {

    private val binder = LocalBinder()
    
    val sessions = CopyOnWriteArrayList<TerminalSession>()

    inner class LocalBinder : Binder() {
        fun getService(): TerminalSessionService = this@TerminalSessionService
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * 启动前台通知守护
     */
    private fun startForegroundNotification() {
        val channelId = "terminal_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Terminal Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the terminal session running in background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // 点击通知返回 Activity
        val intent = Intent(this, TerminalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Terminal Running")
            .setContentText("PRoot Ubuntu environment is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1337, notification)
    }

    /**
     * 创建并注册一个新的终端会话
     */
    fun createSession(context: Context, sessionId: String, onFinished: (TerminalSession) -> Unit): TerminalSession {
        val client = object : TermuxTerminalSessionClientBase() {
            override fun onSessionFinished(finishedSession: TerminalSession) {
                sessions.remove(finishedSession)
                onFinished(finishedSession)
                checkStopSelf()
            }
        }
        val session = PRootEnvironment.createSession(context, sessionId, client)
        sessions.add(session)
        return session
    }

    /**
     * 强制关闭指定会话
     */
    fun killSession(session: TerminalSession) {
        session.finishIfRunning()
        sessions.remove(session)
        checkStopSelf()
    }

    /**
     * 关闭所有会话
     */
    fun killAllSessions() {
        sessions.forEach { it.finishIfRunning() }
        sessions.clear()
        checkStopSelf()
    }

    private fun checkStopSelf() {
        if (sessions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        killAllSessions()
    }
}