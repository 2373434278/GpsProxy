package com.gpsproxy.dev.domain.simulation.model

/**
 * 注入通道的静态目录（身份 + 初始状态）。
 *
 * 名称与说明属于可翻译文案，按 [ChannelId] 在 core/i18n 中解析；
 * 这里只保留与文案无关的事实，避免领域层持有任何用户可见字符串。
 *
 * @param mockAllowed 是否已把本应用设为模拟位置应用（影响官方通道路径的初始备注）
 */
object ChannelCatalog {

    fun initialStates(mockAllowed: Boolean = true): List<ChannelState> = listOf(
        ChannelState(
            id = ChannelId.CONTAINER,
            status = ChannelStatus.OFF,
            note = ChannelNote(ChannelNoteKind.CONTAINER_IDLE),
        ),
        ChannelState(
            id = ChannelId.GPS,
            status = ChannelStatus.OFF,
            note = idleOfficialNote(mockAllowed),
        ),
        ChannelState(
            id = ChannelId.NETWORK,
            status = ChannelStatus.OFF,
            note = idleOfficialNote(mockAllowed),
        ),
        ChannelState(
            id = ChannelId.FUSED,
            status = ChannelStatus.WARN,
            note = ChannelNote(ChannelNoteKind.NOT_FORCED),
        ),
        ChannelState(
            id = ChannelId.SDK,
            status = ChannelStatus.WARN,
            note = ChannelNote(ChannelNoteKind.CONTAINER_ONLY),
        ),
        ChannelState(
            id = ChannelId.SENSOR,
            status = ChannelStatus.OFF,
            note = ChannelNote(ChannelNoteKind.NOT_PROVIDED),
        ),
        ChannelState(
            id = ChannelId.CELL,
            status = ChannelStatus.OFF,
            note = ChannelNote(ChannelNoteKind.CONTAINER_ACTIVE),
        ),
        ChannelState(
            id = ChannelId.WIFI,
            status = ChannelStatus.BAD,
            note = ChannelNote(ChannelNoteKind.HOST_OUT_OF_SCOPE),
        ),
    )

    private fun idleOfficialNote(mockAllowed: Boolean) = ChannelNote(
        if (mockAllowed) ChannelNoteKind.MOCK_STANDBY else ChannelNoteKind.NOT_MOCK_APP,
    )
}
