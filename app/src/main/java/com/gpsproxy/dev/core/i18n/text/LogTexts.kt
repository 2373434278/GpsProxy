package com.gpsproxy.dev.core.i18n.text

/** 诊断日志模板。写入时刻即解析，历史日志不随语言切换回溯。 */
interface LogTexts {
    fun routeHistoryFailed(error: String): String
    fun targetSet(coords: String, address: String): String
    fun singleStarted(channel: String, blurSuffix: String): String
    fun blurSuffix(meters: Int): String
    fun routeStarted(
        kind: String,
        mode: String,
        speedKmh: Int,
        distance: String,
        points: Int,
        source: String,
        blurSuffix: String,
    ): String
    val paused: String
    val resumed: String
    fun speedFactorSet(factor: String): String
    val seekIgnored: String
    fun seeked(percent: Int): String
    val reverseIgnored: String
    fun direction(forward: Boolean): String
    val restarted: String
    val walkIgnored: String
    val stopped: String
    val nudgeIgnored: String
    fun blurEnabled(meters: Int): String
    val blurDisabled: String
    fun pushRetry(attempt: Int, max: Int): String
    fun injectionFailed(title: String): String
    fun injected(
        coords: String,
        speed: String,
        bearing: String,
        accuracy: Int,
        satellites: Int,
        channel: String,
    ): String
    val walkLatitudeLimit: String
    val routeCompleted: String
    fun failure(code: String, cause: String): String
    fun autoStopped(minutes: Int): String
    val pumpCompleted: String
    fun pumpStopped(code: String): String
    val containerUnavailable: String
    val containerNoApps: String
    fun providerIssues(issues: String): String
    fun officialPushFailed(issues: String): String
    fun containerMirrorRetry(millis: Long): String
    fun cellFingerprint(lac: Int, cid: Int, dbm: Int): String
    val locationManagerUnavailable: String
    fun providerDenied(provider: String): String
    val noTestProvider: String
}