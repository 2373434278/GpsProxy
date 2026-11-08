package com.gpsproxy.dev.core.i18n.text

/** 前台服务通知与开机恢复提醒的文案。 */
interface NotificationTexts {
    val channelName: String
    val channelDescription: String
    val titlePaused: String
    val titleCompleted: String
    val titleError: String
    val titleActive: String
    val titleIdle: String
    val noPoint: String
    fun blurSuffix(radiusMeters: Int): String
    fun autoStopSuffix(time: String): String
    fun compactBody(point: String, accuracyMeters: Int): String
    fun detailedBody(
        point: String,
        accuracyMeters: Int,
        coordSystem: String,
        blurSuffix: String,
        autoStopSuffix: String,
    ): String
    val subTextContainer: String
    val subTextOfficial: String
    val resume: String
    val pause: String
    val stop: String

    val bootChannelName: String
    val bootChannelDescription: String
    val bootTitle: String
    fun bootText(point: String): String
    val lastPoint: String
}