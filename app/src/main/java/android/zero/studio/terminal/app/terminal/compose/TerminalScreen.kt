package android.zero.studio.terminal.app.terminal.compose

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = viewModel.currentIndex,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
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
            IconButton(onClick = { viewModel.addSession(context) }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (viewModel.sessions.isNotEmpty()) {
                val currentSession = viewModel.sessions.getOrNull(viewModel.currentIndex)
                if (currentSession != null) {
                    TerminalWorkspace(currentSession)
                }
            } else {
                Text("No active sessions", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TerminalWorkspace(session: TerminalSession) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Column(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    val client = object : TermuxTerminalViewClientBase() {
                        override fun onSingleTapUp(e: MotionEvent) {
                            requestFocus()
                            KeyboardUtils.showSoftKeyboard(ctx, this@apply)
                        }
                    }
                    setTerminalViewClient(client)
                    attachSession(session)
                    setTextSize(40)
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        onScreenUpdated()
                    }
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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) { page ->
            when (page) {
                0 -> {
                    terminalViewRef?.let { terminalView ->
                        AndroidView(
                            factory = { ctx ->
                                ExtraKeysView(ctx, null).apply {
                                    val extraKeysInfo = ExtraKeysInfo(
                                        VIRTUAL_KEYS_JSON,
                                        "default",
                                        android.zero.studio.terminal.shared.termux.extrakeys.ExtraKeysConstants.CONTROL_CHARS_ALIASES
                                    )
                                    val terminalExtraKeys = TerminalExtraKeys(terminalView)
                                    setExtraKeysViewClient(terminalExtraKeys)
                                    setButtonTextColor(onSurfaceColor)
                                    reload(extraKeysInfo, 75f)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                1 -> {
                    var textInput by remember { mutableStateOf("") }
                    AndroidView(
                        factory = { ctx ->
                            EditText(ctx).apply {
                                maxLines = 1
                                isSingleLine = true
                                imeOptions = EditorInfo.IME_ACTION_DONE
                                setTextColor(onSurfaceColor)

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
                            .padding(8.dp),
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