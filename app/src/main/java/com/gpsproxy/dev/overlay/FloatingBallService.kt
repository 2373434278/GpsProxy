package com.gpsproxy.dev.overlay

import com.gpsproxy.dev.core.i18n.StringsHolder

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.MainActivity
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.simulation.model.MotionVector
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.ui.components.BallPanelCard
import com.gpsproxy.dev.ui.nav.OverlayDestination
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.GpsProxyTheme
import com.gpsproxy.dev.ui.theme.Gp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingBallService : Service(), DefaultLifecycleObserver {

    private lateinit var windowManager: WindowManager
    private val overlayLifecycle = OverlayLifecycleOwner()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ballView: ComposeView? = null
    private var panelView: ComposeView? = null
    private lateinit var ballParams: WindowManager.LayoutParams
    private var appInForeground = true
    private var stateJob: Job? = null

    override fun onCreate() {
        super<Service>.onCreate()
        AppGraph.initialize(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        overlayLifecycle.create()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        stateJob = scope.launch {
            AppGraph.engine.state.collect { state ->
                if (state.mode == SimMode.IDLE) {
                    removeAll()
                    stopSelf()
                } else if (!appInForeground) {
                    showBall()
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        appInForeground = true
        hideAll()
    }

    override fun onStop(owner: LifecycleOwner) {
        appInForeground = false
        if (AppGraph.engine.state.value.mode != SimMode.IDLE) {
            showBall()
        }
    }

    private fun showBall() {
        if (ballView != null) return
        val metrics = resources.displayMetrics
        val size = dp(56)
        ballParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels - size - dp(16)).coerceAtLeast(0)
            y = (metrics.heightPixels / 2).coerceAtLeast(0)
        }
        val view = composeView {
            OverlayBall(
                onDrag = { dx, dy ->
                    ballParams.x = (ballParams.x + dx.toInt()).coerceIn(0, metrics.widthPixels - size)
                    ballParams.y = (ballParams.y + dy.toInt()).coerceIn(0, metrics.heightPixels - size)
                    ballView?.let { windowManager.updateViewLayout(it, ballParams) }
                    hidePanel()
                },
                onDragEnd = {
                    ballParams.x = if (ballParams.x + size / 2 < metrics.widthPixels / 2) {
                        dp(8)
                    } else {
                        metrics.widthPixels - size - dp(8)
                    }
                    ballView?.let { windowManager.updateViewLayout(it, ballParams) }
                },
                onClick = {
                    if (panelView == null) showPanel() else hidePanel()
                },
            )
        }
        runCatching {
            windowManager.addView(view, ballParams)
            ballView = view
        }.onFailure {
            stopSelf()
        }
    }

    private fun showPanel() {
        hidePanel()
        val metrics = resources.displayMetrics
        val panelWidth = dp(250)
        val estimatedHeight = dp(470)
        val params = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (ballParams.x + dp(56) - panelWidth).coerceIn(dp(8), (metrics.widthPixels - panelWidth - dp(8)).coerceAtLeast(dp(8)))
            y = (ballParams.y - estimatedHeight).coerceAtLeast(dp(8))
        }
        val view = composeView {
            val state by AppGraph.engine.state.collectAsStateWithLifecycle()
            val settings by AppGraph.settings.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            BallPanelCard(
                paused = state.runState == RunState.PAUSED,
                onClose = { hidePanel() },
                onFavorites = {
                    hideAll()
                    openApp(OverlayDestination.FAVORITES)
                },
                onPick = {
                    hideAll()
                    openApp(OverlayDestination.PICK)
                },
                onPauseResume = {
                    if (state.runState == RunState.PAUSED) {
                        AppGraph.engine.resume()
                    } else {
                        AppGraph.engine.pause()
                    }
                },
                onStop = {
                    AppGraph.engine.stop()
                    removeAll()
                    stopSelf()
                },
                onNudge = { north, east -> AppGraph.engine.nudge(north, east) },
                walkEnabled = state.mode == SimMode.SINGLE && state.runState == RunState.RUNNING,
                walkSpeedKmh = settings.walkSpeedKmh,
                onWalk = { bearing, speed ->
                    AppGraph.engine.setManualMotion(MotionVector(bearing, speed))
                },
                onWalkStop = { AppGraph.engine.setManualMotion(null) },
                onWalkSpeedChange = { kmh ->
                    AppGraph.appScope.launch { AppGraph.settings.setWalkSpeed(kmh) }
                },
            )
        }
        runCatching {
            windowManager.addView(view, params)
            panelView = view
        }
    }

    private fun hidePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
    }

    private fun hideAll() {
        hidePanel()
        ballView?.let { runCatching { windowManager.removeView(it) } }
        ballView = null
    }

    private fun removeAll() {
        hideAll()
    }

    private fun openApp(destination: OverlayDestination) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_ROUTE, destination.raw)
        }
        runCatching { startActivity(intent) }
    }

    private fun composeView(content: @Composable () -> Unit): ComposeView {
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycle)
            setViewTreeSavedStateRegistryOwner(overlayLifecycle)
            setViewTreeViewModelStoreOwner(overlayLifecycle)
            setContent {
                GpsProxyTheme {
                    content()
                }
            }
        }
    }

    override fun onDestroy() {
        stateJob?.cancel()
        stateJob = null
        hideAll()
        runCatching { ProcessLifecycleOwner.get().lifecycle.removeObserver(this) }
        overlayLifecycle.destroy()
        scope.cancel()
        super<Service>.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

@Composable
private fun OverlayBall(
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onDrag = { change, drag ->
                        change.consume()
                        onDrag(drag.x, drag.y)
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF54A8FF), Gp.Blue)))
                .border(1.dp, Color.White.copy(alpha = .45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GpIcons.Crosshair, contentDescription = StringsHolder.text { home.overlayContentDescription }, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun create() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
