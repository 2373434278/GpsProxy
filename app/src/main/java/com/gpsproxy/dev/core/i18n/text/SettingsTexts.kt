package com.gpsproxy.dev.core.i18n.text

import com.gpsproxy.dev.core.i18n.AppLanguage

/**
 * 设置页全部条目。
 *
 * 枚举标签用枚举 `name` 作为键：`core/i18n` 不反向依赖 `domain`，
 * 由 UI 传入 `enum.name`，单测再遍历枚举保证每个键都有文案。
 */
interface SettingsTexts {
    val pageTitle: String
    val generalSection: String
    val coordSystemSection: String
    val locationSection: String
    val payloadSection: String
    val sessionSection: String
    val injectionSection: String
    val startupLogSection: String
    val aboutSection: String

    val languageTitle: String
    val languageSubtitle: String
    fun languageName(language: AppLanguage): String

    val coordSystemTitle: String
    val coordSystemSubtitle: String
    fun coordSystemDescription(system: String): String

    val accuracyTitle: String
    val accuracySubtitle: String
    val driftTitle: String
    val driftSubtitle: String
    val blurTitle: String
    val blurSubtitle: String
    val blurRadiusTitle: String
    val blurRadiusSubtitle: String
    val intervalTitle: String
    val intervalSubtitle: String

    val reportSpeedTitle: String
    val reportSpeedSubtitle: String
    val reportBearingTitle: String
    val reportBearingSubtitle: String
    val altitudeTitle: String
    val altitudeSubtitle: String
    val walkSpeedTitle: String
    val walkSpeedSubtitle: String

    val autoStopTitle: String
    val autoStopSubtitle: String
    val unlimited: String
    fun minutes(value: Int): String

    val channelTitle: String
    val channelSubtitle: String
    fun channelName(channel: String): String
    fun channelDescription(channel: String): String
    val containerRuntimeTitle: String
    val containerRuntimeReady: String
    val containerRuntimeUnavailable: String
    val ready: String
    val unavailable: String
    val containerSpaceTitle: String
    val containerSpaceSubtitle: String
    val overlayTitle: String
    val overlaySubtitle: String

    val lastSelfCheckTitle: String
    val lastSelfCheckNever: String
    fun lastSelfCheckAt(time: String): String
    val never: String
    val checked: String
    val notificationDetailTitle: String
    val notificationDetailSubtitle: String
    fun notificationDetailName(detail: String): String
    fun notificationDetailDescription(detail: String): String
    val bootRestoreTitle: String
    val bootRestoreSubtitle: String
    val logLevelTitle: String
    val logLevelSubtitle: String
    fun detailLevelName(level: String): String
    val clearLogsTitle: String
    val clearLogsSubtitle: String

    val capabilityTitle: String
    val capabilityText: String
    val openDiagnostics: String

    val versionTitle: String
    fun versionSubtitle(version: String, buildType: String): String
    val poiSourceTitle: String
    val poiSourceLive: String
    val poiSourceBuiltin: String
    val amap: String
    val builtin: String
    val supportTitle: String
    val supportSubtitle: String
    val supportValue: String
    val privacy: String

    val accuracyDialogTitle: String
    val intervalDialogTitle: String
    val blurRadiusDialogTitle: String
    val logLevelDialogTitle: String
    val reportSpeedDialogTitle: String
    val reportBearingDialogTitle: String
    val altitudeDialogTitle: String
    val walkSpeedDialogTitle: String
    val autoStopDialogTitle: String
    fun intervalOption(millis: Int): String
    fun accuracyOption(meters: Int): String
    fun kmh(value: Int): String
    fun degrees(value: Int): String
    fun meters(value: Int): String
}
