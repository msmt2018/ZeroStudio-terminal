package android.zero.studio.terminal.app.terminal.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import android.zero.studio.terminal.shared.termux.extrakeys.ExtraKeysConstants
import android.zero.studio.terminal.shared.termux.extrakeys.ExtraKeysInfo
import android.zero.studio.terminal.shared.termux.extrakeys.ExtraKeysView
import android.zero.studio.terminal.shared.termux.extrakeys.SpecialButton
import android.zero.studio.terminal.shared.termux.terminal.TermuxTerminalViewClientBase
import android.zero.studio.terminal.shared.termux.terminal.io.TerminalExtraKeys
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: TerminalViewModel, fragment: Fragment) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    // 加入 imePadding()，使得内部布局在软键盘弹出时自动缩小，底部工具栏被顶起
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {

        // 顶部 TabRow 与 更多菜单
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // 修复 Issue 5: 新建会话时的 IndexOutOfBoundsException
            val safeIndex = if (viewModel.sessions.isEmpty()) 0 else viewModel.currentIndex.coerceIn(0, maxOf(0, viewModel.sessions.lastIndex))

            ScrollableTabRow(
                selectedTabIndex = safeIndex,
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                modifier = Modifier.weight(1f)
            ) {
                if (viewModel.sessions.isEmpty()) {
                    Tab(selected = true, onClick = {}, text = { Text("No Session") })
                } else {
                    viewModel.sessions.forEachIndexed { index, session ->
                        Tab(
                            selected = safeIndex == index,
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
                // 安全获取 Session，防止越界
                val currentSession = viewModel.sessions.getOrNull(viewModel.currentIndex.coerceIn(0, viewModel.sessions.lastIndex))
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
    var extraKeysViewRef by remember { mutableStateOf<ExtraKeysView?>(null) }
    val density = LocalDensity.current.density

    // 监听全局设置，使用 StateFlow
    val fontSize by TerminalSettings.fontSize.collectAsState(initial = 12)
    val keepScreenOn by TerminalSettings.keepScreenOn.collectAsState(initial = false)
    val cursorStyle by TerminalSettings.cursorStyle.collectAsState(initial = 0)
    val bellVibrate by TerminalSettings.bellVibrate.collectAsState(initial = true)

    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    Box(Modifier.fillMaxSize()) {
        // 终端核心
        AndroidView(
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true

                    val client = object : TermuxTerminalViewClientBase() {
                        // 修复 Issue 1 & 4: 单击唤起系统软键盘并确保输入流畅
                        override fun onSingleTapUp(e: MotionEvent) {
                            this@apply.requestFocus()
                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT)
                        }

                        // 修复 Issue 2: 长按唤出复制/粘贴/全选的原生浮动 ActionMode 菜单
                        override fun onLongPress(event: MotionEvent): Boolean {
                            this@apply.showContextMenu()
                            return true
                        }

                        // 修复 Issue 3.2: 组合键底层 API 桥接，返回 ExtraKeysView 的实时状态
                        override fun readControlKey(): Boolean = extraKeysViewRef?.readSpecialButton(SpecialButton.CTRL, true) ?: false
                        override fun readAltKey(): Boolean = extraKeysViewRef?.readSpecialButton(SpecialButton.ALT, true) ?: false
                        override fun readShiftKey(): Boolean = extraKeysViewRef?.readSpecialButton(SpecialButton.SHIFT, true) ?: false
                        override fun readFnKey(): Boolean = extraKeysViewRef?.readSpecialButton(SpecialButton.FN, true) ?: false

                        // 实现终端双指捏合缩放功能
                        override fun onScale(scale: Float): Float {
                            val currentFont = TerminalSettings.fontSize.value.toFloat()
                            val newFontSize = if (scale < 8f || scale > 32f) {
                                (currentFont * scale).coerceIn(8f, 32f)
                            } else {
                                scale.coerceIn(8f, 32f)
                            }
                            TerminalSettings.updateFontSize(newFontSize.toInt())
                            return newFontSize
                        }
                    }

                    // 注释掉阻断原生 ActionMode 的拦截逻辑，解决长按菜单不弹出的问题
                    /*
                    setOnCreateContextMenuListener { menu, _, _ -> ... }
                    */

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

                        // Ignored log handlers
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
                // 修复 Issue 3: 增加 20% 高度 (48dp * 1.2 = ~58dp)
                .height(58.dp)
        ) {
            // 修复 Issue 3: 毛玻璃逻辑。丢弃让所有子元素变糊的 blur 修饰符，改用半透明纯色背景以贴合 Termux 体验且保证字迹清晰。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0x991E1E1E)) // 深色半透明层，代替 blur 避免内容文字变糊
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    // 虚拟按键区
                    0 -> {
                        val virtualKeysView = remember {
                            ExtraKeysView(context, null).apply {
                                val extraKeysInfo = ExtraKeysInfo(
                                    VIRTUAL_KEYS_JSON, "default", ExtraKeysConstants.CONTROL_CHARS_ALIASES
                                )
                                setButtonTextColor(android.graphics.Color.WHITE)
                                // 透明背景以便透出下方的深色半透明背景
                                setButtonBackgroundColor(android.graphics.Color.TRANSPARENT)
                                // 传入计算好的实际像素高度 58dp * density
                                reload(extraKeysInfo, 58f * density)
                            }
                        }

                        LaunchedEffect(terminalViewRef) {
                            terminalViewRef?.let { tv ->
                                virtualKeysView.setExtraKeysViewClient(TerminalExtraKeys(tv))
                                extraKeysViewRef = virtualKeysView
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
