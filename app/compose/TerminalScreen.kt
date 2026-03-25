package android.zero.studio.terminal.app.terminal.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.MotionEvent
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doOnTextChanged
import android.zero.studio.terminal.shared.termux.terminal.TermuxTerminalViewClientBase
import android.zero.studio.terminal.shared.view.KeyboardUtils
import android.zero.studio.terminal.terminal.TerminalSession
import android.zero.studio.terminal.view.TerminalView
import android.zero.studio.terminal.shared.termux.extrakeys.ExtraKeysView
import android.zero.studio.terminal.shared.termux.extrakeys.ExtraKeysInfo
import android.zero.studio.terminal.shared.termux.terminal.io.TerminalExtraKeys

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
 * @author android_zero
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: TerminalViewModel) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

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
                IconButton(onClick = { viewModel.addSession(context) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Session")
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            showMenu = false
                            // 启动设置界面
                            // val intent = Intent(context, SettingsActivity::class.java)
                            // context.startActivity(intent)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Close All Sessions") },
                        onClick = {
                            showMenu = false
                            viewModel.closeAllSessions()
                        }
                    )
                }
            }
        }

        // 核心工作区
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            if (viewModel.sessions.isNotEmpty()) {
                val currentSession = viewModel.sessions.getOrNull(viewModel.currentIndex)
                if (currentSession != null) {
                    TerminalWorkspace(currentSession)
                }
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No active sessions running", color = Color.White)
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
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

    // 缓存一个对剪切板的引用
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    Column(Modifier.fillMaxSize()) {
        
        // 终端区域
        AndroidView(
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    val client = object : TermuxTerminalViewClientBase() {
                        
                        override fun onSingleTapUp(e: MotionEvent) {
                            requestFocus()
                            KeyboardUtils.showSoftKeyboard(ctx, this@apply)
                        }

                        // 支持系统的长按文本选择回调
                        override fun onLongPress(event: MotionEvent): Boolean {
                            return false // 返回 false 交给 View 默认实现，触发自带选择与上下文菜单
                        }

                        // 支持外部按键事件（如实体键盘快捷键等）
                        override fun readControlKey(): Boolean {
                            return false // 如果 ExtraKeys 支持了，这里可以通过委托传递
                        }
                    }

                    session.updateTerminalSessionClient(object : android.zero.studio.terminal.terminal.TerminalSessionClient {
                        override fun onTextChanged(changedSession: TerminalSession) {
                            onScreenUpdated()
                        }
                        override fun onTitleChanged(changedSession: TerminalSession) {}
                        override fun onSessionFinished(finishedSession: TerminalSession) {}
                        
                        // 复制实现
                        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("TerminalText", text))
                            Toast.makeText(ctx, "Text Copied", Toast.LENGTH_SHORT).show()
                        }

                        // 粘贴实现
                        override fun onPasteTextFromClipboard(session: TerminalSession?) {
                            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (text.isNotEmpty()) {
                                this@apply.mEmulator?.paste(text)
                            }
                        }

                        override fun onBell(session: TerminalSession) {}
                        override fun onColorsChanged(session: TerminalSession) {}
                        override fun onTerminalCursorStateChange(state: Boolean) {}
                        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
                        override fun getTerminalCursorStyle(): Int = 0 // BLOCK

                        // Log 实现
                        override fun logError(tag: String, message: String) {}
                        override fun logWarn(tag: String, message: String) {}
                        override fun logInfo(tag: String, message: String) {}
                        override fun logDebug(tag: String, message: String) {}
                        override fun logVerbose(tag: String, message: String) {}
                        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
                        override fun logStackTrace(tag: String, e: Exception) {}
                    })

                    setTerminalViewClient(client)
                    attachSession(session)
                    setTextSize(40)
                    terminalViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            update = { view ->
                if (view.mTermSession != session) {
                    view.attachSession(session)
                }
                view.onScreenUpdated()
            }
        )

        DisposableEffect(session) {
            onDispose {
                terminalViewRef?.let {
                    // 切走时，清除焦点，按需进行回收释放
                    it.clearFocus()
                }
            }
        }

        // 底部控制栏区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp)
                // 高斯模糊/毛玻璃效果支持。
                .background(Color.Black.copy(alpha = 0.5f))
                .blur(radius = 16.dp) 
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    // 虚拟按键
                    0 -> {
                        val virtualKeysView = remember {
                            ExtraKeysView(context, null).apply {
                                val extraKeysInfo = ExtraKeysInfo(
                                    VIRTUAL_KEYS_JSON,
                                    "default",
                                    android.zero.studio.terminal.shared.termux.extrakeys.ExtraKeysConstants.CONTROL_CHARS_ALIASES
                                )
                                terminalViewRef?.let { tv ->
                                    val terminalExtraKeys = TerminalExtraKeys(tv)
                                    setExtraKeysViewClient(terminalExtraKeys)
                                }
                                setButtonTextColor(android.graphics.Color.WHITE) // 因为用了深色毛玻璃，这里用白色文字
                                // 将背景设为透明，以便露出 Box 的毛玻璃
                                setButtonBackgroundColor(android.graphics.Color.TRANSPARENT)
                                reload(extraKeysInfo, 75f)
                            }
                        }
                        
                        // 终端实例更新时重新绑定 Client，而不是重建整个 View
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
                    // 输入行
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
                                    hint = "Enter command here..."
                                    background = null // 去除下划线

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
                                        } else {
                                            false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
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