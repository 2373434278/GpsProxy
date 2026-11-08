package com.gpsproxy.dev.platform.boot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.MainActivity
import com.gpsproxy.dev.R
import com.gpsproxy.dev.domain.session.RecoverableSession

/**
 * 开机后的"上次模拟未结束"提醒。
 *
 * 为什么只发通知、不自动恢复：
 * Android 12+ 禁止从后台启动定位前台服务，开机时静默拉起会被系统直接拒绝；
 * 与其做一个"看起来自动恢复、实际静默失败"的假功能，不如如实提醒用户点一下。
 * 点击后打开应用，上次选点已由设置恢复，用户只需按一次"开始模拟"。
 */
class BootResumeNotifier(
    private val context: Context,
    private val strings: () -> Strings = { StringsHolder.current },
) {

    fun notify(session: RecoverableSession) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val texts = strings().notification
        val point = session.point?.formatted() ?: texts.lastPoint
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(texts.bootTitle)
            .setContentText(texts.bootText(point))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val texts = strings().notification
        val channel = NotificationChannel(
            CHANNEL_ID,
            texts.bootChannelName,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = texts.bootChannelDescription
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "gpsproxy_boot"
        const val NOTIFICATION_ID = 2010
        const val REQUEST_CONTENT = 10
    }
}
