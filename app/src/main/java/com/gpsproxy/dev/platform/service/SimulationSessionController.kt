package com.gpsproxy.dev.platform.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.gpsproxy.dev.location.InjectionService
import com.gpsproxy.dev.overlay.FloatingBallService

/**
 * 前台服务与悬浮窗的生命周期控制。
 *
 * 从 ViewModel 里抽出的原因：这两件事是纯 Android 平台动作，
 * 与业务判断无关；抽出来后 ViewModel 只决定"什么时候调用"，
 * 而"怎么启动、失败怎么处理"有唯一实现。
 *
 * 所有方法都返回是否成功，由调用方决定提示文案，避免这里直接弹 toast。
 */
class SimulationSessionController(private val context: Context) {

    fun startInjectionService(): Boolean = runCatching {
        ContextCompat.startForegroundService(
            context,
            Intent(context, InjectionService::class.java).setAction(InjectionService.ACTION_START),
        )
        true
    }.getOrElse { false }

    fun stopInjectionService() {
        runCatching { context.stopService(Intent(context, InjectionService::class.java)) }
    }

    /**
     * 按设置与授权决定是否显示悬浮球。
     *
     * 未授予悬浮窗权限时静默跳过：权限页会给出明确引导，
     * 这里再弹一次只会打扰用户。
     */
    fun startOverlayIfAllowed(enabled: Boolean): Boolean {
        if (!enabled) return false
        if (!Settings.canDrawOverlays(context)) return false
        return runCatching {
            context.startService(Intent(context, FloatingBallService::class.java))
            true
        }.getOrDefault(false)
    }

    fun stopOverlay() {
        runCatching { context.stopService(Intent(context, FloatingBallService::class.java)) }
    }
}
