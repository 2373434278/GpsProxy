package com.gpsproxy.dev.platform.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.MainActivity
import com.gpsproxy.dev.R
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.settings.NotificationDetail
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import com.gpsproxy.dev.location.InjectionService

/**
 * 前台服务通知的构建工厂。
 *
 * 从 Service 里抽出的原因：
 * - 通知文案包含"是否带 isMock 标记"这种**能力边界**说明，必须与当前通道一致，
 *   旧实现无论选哪条通道都写死 `系统 Mock · isMock=true`，属于误导；
 * - 详略设置（[NotificationDetail]）此前只存不用，现在真正生效；
 * - 通知的暂停/继续/停止动作与 Service 常量绑定，集中在一处避免请求码冲突。
 */
class InjectionNotificationFactory(
    private val context: Context,
    private val settingsProvider: () -> AppSettings,
    private val strings: () -> Strings = { StringsHolder.current },
) {

    fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val texts = strings().notification
        val channel = NotificationChannel(
            CHANNEL_ID,
            texts.channelName,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = texts.channelDescription
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(state: SimulationState): Notification {
        val settings = settingsProvider()
        val texts = strings().notification
        val title = when {
            state.runState == RunState.PAUSED -> texts.titlePaused
            state.runState == RunState.COMPLETED -> texts.titleCompleted
            state.runState == RunState.ERROR -> texts.titleError
            state.isActive -> texts.titleActive
            else -> texts.titleIdle
        }
        val point = state.injectedPoint?.formatted() ?: state.point?.formatted() ?: texts.noPoint
        val blur = if (state.blurRadiusMeters > 0) texts.blurSuffix(state.blurRadiusMeters) else ""
        val autoStop = if (state.autoStopAtMillis > 0) {
            texts.autoStopSuffix(formatClock(state.autoStopAtMillis))
        } else {
            ""
        }
        val text = when (settings.notificationDetail) {
            NotificationDetail.COMPACT -> texts.compactBody(point, state.accuracy)
            NotificationDetail.DETAILED -> texts.detailedBody(
                point,
                state.accuracy,
                settings.coordSystem.label,
                blur,
                autoStop,
            )
        }
        val subText = when (settings.injectionChannel) {
            InjectionChannel.CONTAINER -> texts.subTextContainer
            InjectionChannel.OFFICIAL -> texts.subTextOfficial
        }
        val pauseAction = if (state.runState == RunState.PAUSED) {
            NotificationCompat.Action(0, texts.resume, servicePendingIntent(InjectionService.ACTION_RESUME, 2))
        } else {
            NotificationCompat.Action(0, texts.pause, servicePendingIntent(InjectionService.ACTION_PAUSE, 3))
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(title)
            .setContentText(text)
            .apply {
                if (settings.notificationDetail == NotificationDetail.DETAILED) setSubText(subText)
            }
            .setContentIntent(contentIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(pauseAction)
            .addAction(
                NotificationCompat.Action(
                    0,
                    texts.stop,
                    servicePendingIntent(InjectionService.ACTION_STOP, 4),
                )
            )
            .build()
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, InjectionService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun formatClock(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    companion object {
        const val CHANNEL_ID = "gpsproxy_injection"
        const val NOTIFICATION_ID = 2001
    }
}
