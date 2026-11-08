package com.gpsproxy.dev.location

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.log.LogBus
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.platform.device.AndroidPermissionFacts
import com.gpsproxy.dev.platform.service.ForegroundTypePolicy
import com.gpsproxy.dev.platform.service.InjectionNotificationFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 模拟会话的前台服务。
 *
 * 只做三件事：把 Service 推到前台、接收通知动作、在会话结束后自我停止。
 * 通知的文案/详略/通道标识都在 [InjectionNotificationFactory]，
 * 前台类型的权限规则在 [ForegroundTypePolicy]；Service 本身不再拼这些业务细节。
 */
class InjectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var channelJob: Job? = null

    private lateinit var notifications: InjectionNotificationFactory
    private lateinit var foregroundTypePolicy: ForegroundTypePolicy

    override fun onCreate() {
        super.onCreate()
        AppGraph.initialize(applicationContext)
        notifications = InjectionNotificationFactory(
            applicationContext,
            settingsProvider = { AppGraph.currentSettings() },
        )
        foregroundTypePolicy = ForegroundTypePolicy(AndroidPermissionFacts(applicationContext))
        notifications.createChannel()
        // 渠道名称/说明与通知正文都随界面语言变化；切换语言后立刻重建渠道并刷新通知。
        channelJob = scope.launch {
            AppGraph.i18n.changes.collect {
                notifications.createChannel()
                getSystemService(NotificationManager::class.java)
                    .notify(
                        InjectionNotificationFactory.NOTIFICATION_ID,
                        notifications.build(AppGraph.engine.state.value),
                    )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppGraph.initialize(applicationContext)
        val engine = AppGraph.engine
        when (intent?.action) {
            ACTION_STOP -> {
                engine.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> engine.pause()
            ACTION_RESUME -> engine.resume()
            else -> Unit
        }
        // 系统重启服务时 intent 为 null：会话已结束就不要再起一个空通知
        if (intent == null && !engine.state.value.isActive) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!startForegroundSafely()) {
            stopSelf()
            return START_NOT_STICKY
        }
        observeState()
        return START_STICKY
    }

    private fun startForegroundSafely(): Boolean = try {
        val notification = notifications.build(AppGraph.engine.state.value)
        val type = foregroundTypePolicy.resolve(Build.VERSION.SDK_INT)
        // 三参 startForeground 自 API 29 起存在；类型只在 API 34+ 才需要，
        // 这里显式判断版本，避免 lint 仅凭调用点误判 minSdk。
        if (type != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(InjectionNotificationFactory.NOTIFICATION_ID, notification, type)
        } else {
            startForeground(InjectionNotificationFactory.NOTIFICATION_ID, notification)
        }
        true
    } catch (error: Throwable) {
        LogBus.error("foreground service start failed: ${error.javaClass.simpleName}")
        false
    }

    private fun observeState() {
        if (observerJob != null) return
        observerJob = scope.launch {
            AppGraph.engine.state.collect { state ->
                getSystemService(NotificationManager::class.java)
                    .notify(InjectionNotificationFactory.NOTIFICATION_ID, notifications.build(state))
                if (state.mode == SimMode.IDLE) {
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        observerJob?.cancel()
        observerJob = null
        channelJob?.cancel()
        channelJob = null
        if (AppGraph.engine.state.value.isActive) {
            AppGraph.engine.stop()
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.gpsproxy.dev.action.START"
        const val ACTION_STOP = "com.gpsproxy.dev.action.STOP"
        const val ACTION_PAUSE = "com.gpsproxy.dev.action.PAUSE"
        const val ACTION_RESUME = "com.gpsproxy.dev.action.RESUME"
    }
}
