package com.gpsproxy.dev.core.i18n.text

/** 注入通道的名称、说明、状态备注，以及诊断页文案。 */
interface ChannelTexts {
    fun channelName(id: String): String
    fun channelDescription(id: String): String

    /** [kind] 为领域层 `ChannelNoteKind.name`。 */
    fun note(kind: String, count: Int): String

    fun hitCount(count: Long): String
    val statusMiss: String
    val statusUnavailable: String
    val statusDisabled: String

    val pageTitle: String
    val export: String
    val exportContentDescription: String
    val infoBanner: String
    val channelsSection: String
    val mockFlagSection: String
    val mockFlagContainerTitle: String
    val mockFlagContainerText: String
    val mockFlagOfficialTitle: String
    val mockFlagOfficialText: String
    val blurSection: String
    fun blurOn(radiusMeters: Int): String
    val blurOff: String
    fun blurActiveText(injectedCoords: String, selectedCoords: String, radiusMeters: Int): String
    val blurInactiveText: String
    val errorsSection: String
    val errorNone: String
    fun logsSection(count: Int): String
    val clearLogs: String
    val exportHint: String
    val exportDialogMessage: String
    val exportConfirm: String
    val noLogs: String
}