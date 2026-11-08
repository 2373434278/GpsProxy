package com.gpsproxy.dev.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.log.LogBus
import com.gpsproxy.dev.platform.boot.BootResumeNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机恢复：读取"上次会话是否在运行"，必要时发一条恢复提醒。
 *
 * 不做自动恢复的原因见 [BootResumeNotifier]：Android 12+ 不允许后台启动
 * 定位前台服务，静默拉起只会失败。这里用 `goAsync` 保证异步读取期间
 * 广播接收器不被回收。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppGraph.initialize(appContext)
                val settings = AppGraph.settings.settings.first()
                if (!settings.bootRestore) return@launch
                val session = AppGraph.sessionStore.last() ?: return@launch
                if (!session.wasActive) return@launch
                BootResumeNotifier(appContext).notify(session)
                LogBus.info("boot resume notification posted")
            } catch (error: Throwable) {
                LogBus.warn("boot resume skipped: ${error.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }
}
