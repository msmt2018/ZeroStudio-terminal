package android.zero.studio.terminal.app.terminal.proot

import android.content.Context
import android.os.Build
import android.zero.studio.terminal.terminal.TerminalSession
import android.zero.studio.terminal.terminal.TerminalSessionClient
import android.zero.studio.terminal.utils.DownloadConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 封装 PRoot 与 Ubuntu RootFS 的下载、解压与会话环境配置工作。
 * @author android_zero
 */
object PRootEnvironment {

    fun localDir(context: Context) = File(context.filesDir.parentFile, "proot_local").apply { mkdirs() }
    fun localBinDir(context: Context) = File(localDir(context), "bin").apply { mkdirs() }
    fun localLibDir(context: Context) = File(localDir(context), "lib").apply { mkdirs() }
    fun sandboxDir(context: Context) = File(localDir(context), "sandbox").apply { mkdirs() }
    fun sandboxHomeDir(context: Context) = File(context.getExternalFilesDir(null), "home").apply { mkdirs() }
    fun tmpDir(context: Context) = File(context.cacheDir, "proot_tmp").apply { mkdirs() }

    fun isEnvironmentInstalled(context: Context): Boolean {
        val rootfs = sandboxDir(context).listFiles()?.filter {
            it.name != "tmp"
        } ?: emptyList()
        return File(localDir(context), ".terminal_setup_ok_DO_NOT_REMOVE").exists() && rootfs.isNotEmpty()
    }

    fun markEnvironmentInstalled(context: Context) {
        File(localDir(context), ".terminal_setup_ok_DO_NOT_REMOVE").createNewFile()
    }

    fun getProotUrl(): String {
        val abi = Build.SUPPORTED_ABIS
        return when {
            abi.contains("x86_64") -> DownloadConstants.PROOT_X64
            abi.contains("arm64-v8a") -> DownloadConstants.PROOT_ARM64
            abi.contains("armeabi-v7a") -> DownloadConstants.PROOT_ARM
            else -> throw RuntimeException("Unsupported CPU ABI for PRoot")
        }
    }

    fun getTallocUrl(): String {
        val abi = Build.SUPPORTED_ABIS
        return when {
            abi.contains("x86_64") -> DownloadConstants.TALLOC_X64
            abi.contains("arm64-v8a") -> DownloadConstants.TALLOC_ARM64
            abi.contains("armeabi-v7a") -> DownloadConstants.TALLOC_ARM
            else -> throw RuntimeException("Unsupported CPU ABI for LibTalloc")
        }
    }

    fun getRootfsUrl(): String {
        val abi = Build.SUPPORTED_ABIS
        return when {
            abi.contains("x86_64") -> DownloadConstants.ROOTFS_X64
            abi.contains("arm64-v8a") -> DownloadConstants.ROOTFS_ARM64
            abi.contains("armeabi-v7a") -> DownloadConstants.ROOTFS_ARM
            else -> throw RuntimeException("Unsupported CPU ABI for RootFS")
        }
    }

    /**
     * 高可靠性下载：带有 OkHttp 超时机制与 3 次重试保护，防止弱网中断
     */
    suspend fun downloadFileWithRetry(urlStr: String, outputFile: File, onProgress: (Long, Long) -> Unit) {
        var attempt = 0
        while (attempt < 3) {
            try {
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(2, TimeUnit.MINUTES)
                        .readTimeout(2, TimeUnit.MINUTES)
                        .writeTimeout(2, TimeUnit.MINUTES)
                        .callTimeout(30, TimeUnit.MINUTES) // 允许总体长达30分钟的下载时间
                        .build()

                    val request = Request.Builder().url(urlStr).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

                        val body = response.body ?: throw IOException("Empty response body")
                        val totalBytes = body.contentLength()
                        var downloadedBytes = 0L

                        outputFile.parentFile?.mkdirs()
                        outputFile.outputStream().use { output ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(16 * 1024)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead
                                    withContext(Dispatchers.Main) {
                                        onProgress(downloadedBytes, totalBytes)
                                    }
                                }
                            }
                        }
                        // 校验完整性
                        if (totalBytes > 0 && downloadedBytes < totalBytes) {
                            throw IOException("Incomplete download")
                        }
                    }
                }
                return // 下载成功，退出重试循环
            } catch (e: Exception) {
                attempt++
                if (attempt >= 3) throw e
                delay(1500) // 重试前等待
            }
        }
    }

    fun extractAssets(context: Context) {
        val binDir = localBinDir(context)
        val scripts = listOf("init.sh", "sandbox.sh", "setup.sh", "utils.sh", "universal_runner.sh", "termux-x11.sh")
        scripts.forEach { script ->
            try {
                context.assets.open("terminal/$script").use { input ->
                    val outFile = File(binDir, script.removeSuffix(".sh"))
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    outFile.setExecutable(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val lspDir = File(binDir, "lsp").apply { mkdirs() }
        try {
            val lspScripts = context.assets.list("terminal/lsp") ?: emptyArray()
            lspScripts.forEach { script ->
                context.assets.open("terminal/lsp/$script").use { input ->
                    val outFile = File(lspDir, script.removeSuffix(".sh"))
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    outFile.setExecutable(true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        File(localDir(context), "stat").writeText("cpu  1957 0 2877 93280 262 342 254 87 0 0\nctxt 140223\nbtime 1680020856\nprocesses 772\nprocs_running 2\nprocs_blocked 0\n")
        File(localDir(context), "vmstat").writeText("nr_free_pages 1743136\nnr_inactive_anon 179281\nnr_active_anon 7183\n")
    }

    /**
     * 组装运行 PRoot 环境所必须的 Linux 环境变量
     */
    fun getEnv(context: Context, sessionId: String, cwd: String): Array<String> {
        val sessionTmp = File(tmpDir(context), sessionId).apply { mkdirs() }
        val map = mutableMapOf(
            "PROOT_TMP_DIR" to sessionTmp.absolutePath,
            "WKDIR" to cwd,
            "PUBLIC_HOME" to (context.getExternalFilesDir(null)?.absolutePath ?: ""),
            "COLORTERM" to "truecolor",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "LOCAL" to localDir(context).absolutePath,
            "PRIVATE_DIR" to context.filesDir.parentFile!!.absolutePath,
            "LD_LIBRARY_PATH" to localLibDir(context).absolutePath,
            "EXT_HOME" to sandboxHomeDir(context).absolutePath,
            "HOME" to "/home",
            "PROMPT_DIRTRIM" to "2",
            "TMP_DIR" to context.cacheDir.absolutePath,
            "TMPDIR" to context.cacheDir.absolutePath,
            "TZ" to "UTC",
            "DISPLAY" to ":0",
            "FDROID" to "true",
            "SANDBOX" to "true",
            "LINKER" to if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"
        )
        val existingPath = System.getenv("PATH") ?: ""
        map["PATH"] = "${localBinDir(context).absolutePath}:$existingPath"

        return map.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    fun createSession(context: Context, sessionId: String, client: TerminalSessionClient): TerminalSession {
        val env = getEnv(context, sessionId, sandboxHomeDir(context).absolutePath)
        val sandboxSH = File(localBinDir(context), "sandbox")
        val args = arrayOf("-c", sandboxSH.absolutePath)
        val session = TerminalSession("/system/bin/sh", localDir(context).absolutePath, args, env, 2000, client)
        session.mSessionName = sessionId
        return session
    }

    fun createSetupSession(context: Context, client: TerminalSessionClient): TerminalSession {
        val env = getEnv(context, "setup", sandboxHomeDir(context).absolutePath)
        val setupSH = File(localBinDir(context), "setup")
        val sandboxSH = File(localBinDir(context), "sandbox")
        val args = arrayOf("-c", setupSH.absolutePath, sandboxSH.absolutePath)
        val session = TerminalSession("/system/bin/sh", localDir(context).absolutePath, args, env, 2000, client)
        session.mSessionName = "Setup"
        return session
    }
}