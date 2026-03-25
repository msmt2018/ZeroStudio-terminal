package android.zero.studio.terminal.app.terminal.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import android.zero.studio.terminal.R
import android.zero.studio.terminal.shared.termux.extrakeys.ExtraKeysConstants
import android.zero.studio.terminal.shared.termux.extrakeys.ExtraKeysInfo
import android.zero.studio.terminal.shared.termux.extrakeys.ExtraKeysView
import android.zero.studio.terminal.shared.termux.terminal.TermuxTerminalViewClientBase
import android.zero.studio.terminal.shared.termux.terminal.io.TerminalExtraKeys
import android.zero.studio.terminal.shared.view.KeyboardUtils
import android.zero.studio.terminal.terminal.TerminalSession
import android.zero.studio.terminal.view.TerminalView

const val VIRTUAL_KEYS_JSON = """[[
    "ESC",
    {"key":"<","popup":""},
    {"key":">","popup":""},
    {"key":"BACKSLASH","popup":""},
    {"key":"=","popup":""},
    {"key":"^","popup":""},
    {"key":"$","popup":""},
    {"key":"(","popup":")"},
    {"key":"{","popup":"}"},
    {"key":"[","popup":"]"},
    "ENTER"
],[
    "TAB",
    {"key":"&","popup":""},
    {"key":";","popup":""},
    {"key":"/","popup":"~"},
    {"key":"%","popup":""},
    {"key":"*","popup":""},
    "HOME","UP","END","PGUP"
],[
    "CTRL","FN","ALT",
    {"key":"|","popup":""},
    {"key":"-","popup":"+"},
    {"key":"QUOTE","popup":""},
    "LEFT","DOWN","RIGHT","PGDN"
]]"""


/**
 * 生产级 Terminal UI 容器。
 * 解决了双指缩放、剪切板、上下文菜单、虚拟键盘卡顿及沉浸式问题。
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: TerminalViewModel, fragment: Fragment) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    // 避开系统状态栏与底部导航栏，防止重叠
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        
        // 顶部 TabRow 与 更多菜单
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            ScrollableTabRow(
                selectedTabIndex = if (viewModel.sessions.isEmpty()) 0 else viewModel.currentIndex,
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                modifier = Modifier.weight(1f)
            ) {
                if (viewModel.sessions.isEmpty()) {
                    Tab(selected = true, onClick = {}, text = { Text("No Session") })
                } else {
                    viewModel.sessions.forEachIndexed { index, session ->
                        Tab(
                            selected = viewModel.currentIndex == index,
                            onClick = { viewModel.currentIndex = index },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(session.mSessionName ?: "Session ${index + 1}")
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { viewModel.closeSession(index) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // 右侧菜单 (添加新会话、关闭所有会话、打开设置 Fragment)
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("New Session") },
                        onClick = { showMenu = false; viewModel.addSession(context) }
                    )
                    DropdownMenuItem(
                        text = { Text("Close All Sessions") },
                        onClick = { showMenu = false; viewModel.closeAllSessions() }
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            showMenu = false
                            fragment.parentFragmentManager.beginTransaction()
                                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                                .replace((fragment.requireView().parent as ViewGroup).id, TerminalSettingsFragment())
                                .addToBackStack(null)
                                .commit()
                        }
                    )
                }
            }
        }

        // 核心终端区域
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (viewModel.sessions.isNotEmpty()) {
                val currentSession = viewModel.sessions.getOrNull(viewModel.currentIndex)
                if (currentSession != null) {
                    TerminalWorkspace(currentSession)
                }
            } else {
                // 无会话时的空白状态提示页
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No active terminal sessions", color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.addSession(context) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New Terminal Session")
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TerminalWorkspace(session: TerminalSession) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val context = LocalContext.current
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    
    // 监听全局设置，使用 StateFlow (须提前在应用启动或 viewModel 中 init TerminalSettings)
    val fontSize by TerminalSettings.fontSize.collectAsState(initial = 12)
    val keepScreenOn by TerminalSettings.keepScreenOn.collectAsState(initial = false)
    val cursorStyle by TerminalSettings.cursorStyle.collectAsState(initial = 0)
    val bellVibrate by TerminalSettings.bellVibrate.collectAsState(initial = true)

    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    Box(Modifier.fillMaxSize()) {
        // 1. 终端核心
        AndroidView(
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    val client = object : TermuxTerminalViewClientBase() {
                        override fun onSingleTapUp(e: MotionEvent) {
                            requestFocus()
                            KeyboardUtils.showSoftKeyboard(ctx, this@apply)
                        }

                        override fun onLongPress(event: MotionEvent): Boolean {
                            return false // 交给原生 View 处理长按 TextSelection
                        }

                        // 【新增要求】：实现终端双指捏合缩放功能
                        override fun onScale(scale: Float): Float {
                            val currentFont = TerminalSettings.fontSize.value.toFloat()
                            // 如果 scale 的值大幅偏离正常范围（初次触发），利用 currentFont 和变化比例计算新的字号
                            val newFontSize = if (scale < 8f || scale > 32f) {
                                (currentFont * scale).coerceIn(8f, 32f)
                            } else {
                                scale.coerceIn(8f, 32f)
                            }
                            // 更新全局字体设置
                            TerminalSettings.updateFontSize(newFontSize.toInt())
                            return newFontSize
                        }
                    }

                    // 设置自定义的 Context Menu 监听器，支持复制/粘贴
                    setOnCreateContextMenuListener { menu, _, _ ->
                        menu.add(0, 1, 0, "Copy").setOnMenuItemClickListener {
                            val selected = this@apply.selectedText
                            if (!selected.isNullOrEmpty()) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", selected))
                                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                                this@apply.stopTextSelectionMode()
                            }
                            true
                        }
                        menu.add(0, 2, 0, "Paste").setOnMenuItemClickListener {
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (text.isNotEmpty()) this@apply.mEmulator?.paste(text)
                            true
                        }
                        menu.add(0, 3, 0, "Share").setOnMenuItemClickListener {
                            val selected = this@apply.selectedText
                            if (!selected.isNullOrEmpty()) {
                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, selected)
                                }
                                ctx.startActivity(android.content.Intent.createChooser(sendIntent, "Share via"))
                                this@apply.stopTextSelectionMode()
                            }
                            true
                        }
                    }

                    // 将配置与操作注入会话
                    session.updateTerminalSessionClient(object : android.zero.studio.terminal.terminal.TerminalSessionClient {
                        override fun onTextChanged(changedSession: TerminalSession) = onScreenUpdated()
                        override fun onTitleChanged(changedSession: TerminalSession) {}
                        override fun onSessionFinished(finishedSession: TerminalSession) {}
                        
                        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
                        }

                        override fun onPasteTextFromClipboard(session: TerminalSession?) {
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (text.isNotEmpty()) this@apply.mEmulator?.paste(text)
                        }

                        override fun onBell(session: TerminalSession) {
                            if (bellVibrate) {
                                val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(50)
                                }
                            }
                        }

                        override fun onColorsChanged(session: TerminalSession) {}
                        override fun onTerminalCursorStateChange(state: Boolean) {}
                        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
                        override fun getTerminalCursorStyle(): Int = cursorStyle
                        
                        // 占位的 Logger 实现
                        override fun logError(t: String, m: String) {}
                        override fun logWarn(t: String, m: String) {}
                        override fun logInfo(t: String, m: String) {}
                        override fun logDebug(t: String, m: String) {}
                        override fun logVerbose(t: String, m: String) {}
                        override fun logStackTraceWithMessage(t: String, m: String, e: Exception) {}
                        override fun logStackTrace(t: String, e: Exception) {}
                    })

                    setTerminalViewClient(client)
                    attachSession(session)
                    terminalViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize().background(Color.Black),
            update = { view ->
                if (view.mTermSession != session) {
                    view.attachSession(session)
                }
                // 运用字号，通常使用 sp 的密度缩放
                val density = context.resources.displayMetrics.scaledDensity
                view.setTextSize((fontSize * density).toInt())
                view.keepScreenOn = keepScreenOn
                view.mEmulator?.setCursorStyle()
                view.onScreenUpdated()
            }
        )

        // 安全回收释放焦点与内存
        DisposableEffect(session) {
            onDispose {
                terminalViewRef?.clearFocus()
            }
        }

        // 底部悬浮控制栏
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(48.dp)
                // 启用毛玻璃模糊效果
                .background(Color(0x66000000))
                .blur(radius = 16.dp) 
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    // 虚拟按键区
                    0 -> {
                        // 隔离到 Factory 初始化，避免 update 频繁重建导致的按键卡死
                        val virtualKeysView = remember {
                            ExtraKeysView(context, null).apply {
                                val extraKeysInfo = ExtraKeysInfo(
                                    VIRTUAL_KEYS_JSON, "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES
                                )
                                setButtonTextColor(android.graphics.Color.WHITE)
                                // 透明背景以便漏出毛玻璃层
                                setButtonBackgroundColor(android.graphics.Color.TRANSPARENT)
                                reload(extraKeysInfo, 48f)
                            }
                        }
                        
                        LaunchedEffect(terminalViewRef) {
                            terminalViewRef?.let { tv ->
                                virtualKeysView.setExtraKeysViewClient(TerminalExtraKeys(tv))
                            }
                        }

                        AndroidView(
                            factory = { virtualKeysView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // 快捷输入行
                    1 -> {
                        var textInput by remember { mutableStateOf("") }
                        AndroidView(
                            factory = { ctx ->
                                EditText(ctx).apply {
                                    maxLines = 1
                                    isSingleLine = true
                                    imeOptions = EditorInfo.IME_ACTION_DONE
                                    setTextColor(android.graphics.Color.WHITE)
                                    setHintTextColor(android.graphics.Color.LTGRAY)
                                    hint = "Command input..."
                                    background = null 

                                    doOnTextChanged { text, _, _, _ ->
                                        textInput = text.toString()
                                    }

                                    setOnEditorActionListener { _, actionId, _ ->
                                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                                            terminalViewRef?.let { tv ->
                                                if (textInput.isEmpty()) {
                                                    tv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                                                    tv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                                                } else {
                                                    tv.mTermSession?.write(textInput)
                                                    tv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                                                    tv.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                                                    setText("")
                                                }
                                            }
                                            true
                                        } else false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            update = { editText ->
                                if (editText.text.toString() != textInput) {
                                    editText.setText(textInput)
                                    editText.setSelection(textInput.length)
                                }
                            }
                        )
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
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(message, color = Color.White)
    }
}

@Composable
fun DownloadingScreen(state: TerminalState.Downloading) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Downloading ${state.fileName}...", color = Color.White)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(0.8f),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text("${state.downloadedMb} MB / ${state.totalMb} MB", color = Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(session: TerminalSession) {
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