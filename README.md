
### Introduction: 
 -> This terminal is developed using the latest official source code of termux and combined with the xed editor for terminal rootfs usage
UI interface development technology points:
 - 1 compose + material3
 - 2 fragment +activity ：Fragmented pages, quickly integrate any activity
 -  3 tab layout：Quickly manage multiple sessions
 -  4 Support loading/installing/starting Linux bootstrap using termux terminal mode.
 -  Mainly:Simultaneously supports pro loading/installing/starting any Linux rootfs
The two methods mentioned above only require the use of corresponding activities.
The native boot package of termux needs to be rebuilt by oneself


![1000666180](https://github.com/user-attachments/assets/2ac1c9c7-a661-4528-a675-6a38100cadef)



At present, there are still the following issues that need to be resolved, which will be resolved in the next few days：

English：
Pending Issues List:
Keyboard Trigger: Clicking the terminal view area fails to invoke the Android system soft keyboard.
Context Menu: Long-pressing the terminal view area does not trigger the floating context menu (Copy, Paste, Cut, Multi-select, and Select All).
Input Toolbar Effects:
The Gaussian blur/frosted glass effect on the bottom symbol input toolbar is incorrectly applied to the top layer. It should be applied to the background widget behind the text/symbols, not over them. Currently, all text and editing controls are blurred and invisible.
Increase the height of the symbol input toolbar by 20%.
3.1 Keyboard Sync: The symbol input toolbar must be bound to the system soft keyboard; it should follow the keyboard's pop-up/dismissal animations and stay pinned directly above the input method.
3.2 Symbol Logic: Symbol inputs should function like Termux, supporting combined inputs (e.g., Ctrl + Z). Utilize existing Termux APIs and class objects rather than reinventing the wheel.
Input Fluidity: Ensure the terminal's input area supports smooth text entry and pasting. Standard keys like Enter and Space from the soft keyboard must function correctly for Linux commands.
Stability: The application crashes when creating a new session.：
FATAL EXCEPTION: main
Process: android.zero.studio.terminal, PID: 20134
java.lang.IndexOutOfBoundsException: Index 1 out of bounds for length 1
	at jdk.internal.util.Preconditions.outOfBounds(Preconditions.java:64)
	at jdk.internal.util.Preconditions.outOfBoundsCheckIndex(Preconditions.java:70)
	at jdk.internal.util.Preconditions.checkIndex(Preconditions.java:266)
	at java.util.Objects.checkIndex(Objects.java:359)
	at java.util.ArrayList.get(ArrayList.java:434)
	at androidx.compose.material3.TabRowKt$ScrollableTabRow$1.invoke(SourceFile:1409)
	at androidx.compose.material3.TabRowKt$ScrollableTabRow$1.invoke(SourceFile:1407)
	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(SourceFile:131)
	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke$lambda$0(SourceFile:133)
	at androidx.compose.runtime.internal.ComposableLambdaImpl.$r8$lambda$qz3voikrQeNn5XJEtUlXR2wfzBw(SourceFile:0)
	at androidx.compose.runtime.internal.ComposableLambdaImpl$$ExternalSyntheticLambda1.invoke(SourceFile:0)
	at androidx.compose.runtime.RecomposeScopeImpl.compose(SourceFile:201)
	at androidx.compose.runtime.ComposerImpl.recomposeToGroupEnd(SourceFile:1690)
	at androidx.compose.runtime.ComposerImpl.skipCurrentGroup(SourceFile:2026)
	at androidx.compose.runtime.ComposerImpl.doCompose-aFTiNEg(SourceFile:2659)
	at androidx.compose.runtime.ComposerImpl.recompose-aFTiNEg$runtime(SourceFile:2583)
	at androidx.compose.runtime.CompositionImpl.recompose(SourceFile:1080)
	at androidx.compose.runtime.Recomposer.performRecompose(SourceFile:1406)
	at androidx.compose.runtime.Recomposer.access$performRecompose(SourceFile:159)
	at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.invokeSuspend$lambda$2(SourceFile:638)
	at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.$r8$lambda$sdKIQuFT6MpOW8QdHT9yWSawFoM(SourceFile:0)
	at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2$$ExternalSyntheticLambda0.invoke(SourceFile:0)
	at androidx.compose.ui.platform.AndroidUiFrameClock$withFrameNanos$2$callback$1.doFrame(SourceFile:39)
	at androidx.compose.ui.platform.AndroidUiDispatcher.performFrameDispatch(SourceFile:108)
	at androidx.compose.ui.platform.AndroidUiDispatcher.access$performFrameDispatch(SourceFile:41)
	at androidx.compose.ui.platform.AndroidUiDispatcher$dispatchCallback$1.doFrame(SourceFile:69)
	at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1745)
	at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1756)
	at android.view.Choreographer.doCallbacks(Choreographer.java:1270)
	at android.view.Choreographer.doFrame(Choreographer.java:1159)
	at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:1710)
	at android.os.Handler.handleCallback(Handler.java:959)
	at android.os.Handler.dispatchMessage(Handler.java:100)
	at android.os.Looper.loopOnce(Looper.java:249)
	at android.os.Looper.loop(Looper.java:337)
	at android.app.ActivityThread.main(ActivityThread.java:9604)
	at java.lang.reflect.Method.invoke(Native Method)
	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:615)
	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:936)
	Suppressed: kotlinx.coroutines.internal.DiagnosticCoroutineContextException: [androidx.compose.runtime.PausableMonotonicFrameClock@bfdb94a, androidx.compose.ui.platform.MotionDurationScaleImpl@2b8a0bb, StandaloneCoroutine{Cancelling}@53799d8, AndroidUiDispatcher@e9c8131]

中文：
需要修复以下问题：
1.点击终端视图区域无法拉起安卓系统软键盘
2.长按终端视图区域没有复制粘贴剪切多选以及上下文（也就是复制粘贴剪切多选全选）浮动菜单弹出
3.底部符号输入工具栏的高斯模糊毛玻璃效果是设置在文字符号后面的背景工具栏控件，不是覆盖在文字符号和工具栏最顶层，现在是所有文字符号和工具栏编辑都模糊了，完全看不见设置的文字符号。然后符号输入工具栏高度再增加百分之二十的高度。
3.1 ：因为终端视图区域需要拉起系统软键盘输入法来输入文字，所以拉起系统软键盘的同时符号输入工具栏这个控件需要绑定跟随系统软键盘输入法的弹出与关闭期间的跟随然后置于系统软键盘输入法顶。
3.2符号输入工具栏的符号需要和termux的符号输入工具栏一样，每一个符号都可以组合输入使用，比如Ctrl +z 等。这个完全可以使用termux以及做的有的API源码与class对象实例，没必要重复造轮子。

4.确保终端视图区域的输入区可以流畅无阻输入文字，粘贴输入等，比如输入Linux命令，软键盘输入法的回车空格等key都可以正常使用与输入。
5.新建会话时会崩溃：FATAL EXCEPTION: main
Process: android.zero.studio.terminal, PID: 20134
java.lang.IndexOutOfBoundsException: Index 1 out of bounds for length 1
	at jdk.internal.util.Preconditions.outOfBounds(Preconditions.java:64)
	at jdk.internal.util.Preconditions.outOfBoundsCheckIndex(Preconditions.java:70)
	at jdk.internal.util.Preconditions.checkIndex(Preconditions.java:266)
	at java.util.Objects.checkIndex(Objects.java:359)
	at java.util.ArrayList.get(ArrayList.java:434)
	at androidx.compose.material3.TabRowKt$ScrollableTabRow$1.invoke(SourceFile:1409)
	at androidx.compose.material3.TabRowKt$ScrollableTabRow$1.invoke(SourceFile:1407)
	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(SourceFile:131)
	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke$lambda$0(SourceFile:133)
	at androidx.compose.runtime.internal.ComposableLambdaImpl.$r8$lambda$qz3voikrQeNn5XJEtUlXR2wfzBw(SourceFile:0)
	at androidx.compose.runtime.internal.ComposableLambdaImpl$$ExternalSyntheticLambda1.invoke(SourceFile:0)
	at androidx.compose.runtime.RecomposeScopeImpl.compose(SourceFile:201)
	at androidx.compose.runtime.ComposerImpl.recomposeToGroupEnd(SourceFile:1690)
	at androidx.compose.runtime.ComposerImpl.skipCurrentGroup(SourceFile:2026)
	at androidx.compose.runtime.ComposerImpl.doCompose-aFTiNEg(SourceFile:2659)
	at androidx.compose.runtime.ComposerImpl.recompose-aFTiNEg$runtime(SourceFile:2583)
	at androidx.compose.runtime.CompositionImpl.recompose(SourceFile:1080)
	at androidx.compose.runtime.Recomposer.performRecompose(SourceFile:1406)
	at androidx.compose.runtime.Recomposer.access$performRecompose(SourceFile:159)
	at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.invokeSuspend$lambda$2(SourceFile:638)
	at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.$r8$lambda$sdKIQuFT6MpOW8QdHT9yWSawFoM(SourceFile:0)
	at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2$$ExternalSyntheticLambda0.invoke(SourceFile:0)
	at androidx.compose.ui.platform.AndroidUiFrameClock$withFrameNanos$2$callback$1.doFrame(SourceFile:39)
	at androidx.compose.ui.platform.AndroidUiDispatcher.performFrameDispatch(SourceFile:108)
	at androidx.compose.ui.platform.AndroidUiDispatcher.access$performFrameDispatch(SourceFile:41)
	at androidx.compose.ui.platform.AndroidUiDispatcher$dispatchCallback$1.doFrame(SourceFile:69)
	at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1745)
	at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1756)
	at android.view.Choreographer.doCallbacks(Choreographer.java:1270)
	at android.view.Choreographer.doFrame(Choreographer.java:1159)
	at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:1710)
	at android.os.Handler.handleCallback(Handler.java:959)
	at android.os.Handler.dispatchMessage(Handler.java:100)
	at android.os.Looper.loopOnce(Looper.java:249)
	at android.os.Looper.loop(Looper.java:337)
	at android.app.ActivityThread.main(ActivityThread.java:9604)
	at java.lang.reflect.Method.invoke(Native Method)
	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:615)
	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:936)
	Suppressed: kotlinx.coroutines.internal.DiagnosticCoroutineContextException: [androidx.compose.runtime.PausableMonotonicFrameClock@bfdb94a, androidx.compose.ui.platform.MotionDurationScaleImpl@2b8a0bb, StandaloneCoroutine{Cancelling}@53799d8, AndroidUiDispatcher@e9c8131]


  ![1000666180](https://github.com/user-attachments/assets/4e13e623-3919-42d5-9149-23a5c7c18fd6)
