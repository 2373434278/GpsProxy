package com.gpsproxy.dev.domain.simulation.channel

import com.gpsproxy.dev.domain.simulation.model.ChannelId
import com.gpsproxy.dev.domain.simulation.model.ChannelNote
import com.gpsproxy.dev.domain.simulation.model.ChannelNoteKind
import com.gpsproxy.dev.domain.simulation.model.ChannelState
import com.gpsproxy.dev.domain.simulation.model.ChannelStatus
import com.gpsproxy.dev.domain.simulation.port.InstallReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 通道状态的唯一所有者。
 *
 * 旧实现里通道状态被 5 个私有方法分别 `_channels.update { … }`：
 * `syncChannels`、`recordHits`、`recordChannelHit`、`updateChannel`、
 * `markChannelsStopped`、`refreshChannels`，文案与状态散落在控制器各处。
 * 现在只有这一个类能改通道状态，决策（何时置 OK/BAD）仍由引擎调用。
 *
 * 说明：这里刻意不写日志——通道状态是「事实投影」，日志由触发动作的一方记录，
 * 避免同一件事在日志里出现两次。
 */
class ChannelMonitor(initial: List<ChannelState>) {

    private val _states = MutableStateFlow(initial)
    val states: StateFlow<List<ChannelState>> = _states.asStateFlow()

    /** 容器通道就绪，且已接管若干应用。 */
    fun markContainerReady(count: Int) = update(ChannelId.CONTAINER) {
        it.copy(status = ChannelStatus.OK, note = ChannelNote(ChannelNoteKind.CONTAINER_READY, count))
    }

    /** 容器可用，但没有任何已导入应用可以接管。 */
    fun markContainerEmpty() = update(ChannelId.CONTAINER) {
        it.copy(status = ChannelStatus.WARN, note = ChannelNote(ChannelNoteKind.CONTAINER_EMPTY))
    }

    /** 容器运行时不可用。 */
    fun markContainerUnavailable() = update(ChannelId.CONTAINER) {
        it.copy(status = ChannelStatus.BAD, note = ChannelNote(ChannelNoteKind.CONTAINER_UNAVAILABLE))
    }

    /** 按官方提供者挂载结果刷新 GPS / Network / Fused 三个通道。 */
    fun onInstalled(report: InstallReport) {
        val installed = report.installed.toSet()
        _states.update { list ->
            list.map { channel ->
                when (channel.id) {
                    ChannelId.GPS, ChannelId.NETWORK -> if (channel.id in installed) {
                        channel.copy(status = ChannelStatus.OK, note = ChannelNote(ChannelNoteKind.MOUNTED_OFFICIAL))
                    } else {
                        channel.copy(status = ChannelStatus.BAD, note = ChannelNote(ChannelNoteKind.NOT_MOUNTED))
                    }
                    ChannelId.FUSED -> if (ChannelId.FUSED in installed) {
                        channel.copy(status = ChannelStatus.OK, note = ChannelNote(ChannelNoteKind.MOUNTED_FUSED))
                    } else {
                        channel.copy(status = ChannelStatus.WARN, note = ChannelNote(ChannelNoteKind.FUSED_NOT_EXPOSED))
                    }
                    else -> channel
                }
            }
        }
    }

    /** 一次写入命中了多个通道（官方通道可能同时写 GPS + Network + Fused）。 */
    fun recordHits(hits: List<ChannelId>) {
        if (hits.isEmpty()) return
        val hitSet = hits.toSet()
        _states.update { list ->
            list.map { channel ->
                if (channel.id in hitSet) {
                    channel.copy(count = channel.count + 1, status = ChannelStatus.OK)
                } else {
                    channel
                }
            }
        }
    }

    /** 单个通道命中（容器 / 基站），附带"随坐标同步"的说明。 */
    fun recordHit(id: ChannelId) = update(id) {
        it.copy(count = it.count + 1, status = ChannelStatus.OK, note = ChannelNote(ChannelNoteKind.SYNCED))
    }

    /**
     * 按当前环境刷新提示文案。
     *
     * 运行中不做改写：此时通道状态来自真实写入结果，环境探测不应覆盖它。
     */
    fun refreshEnvironment(mockAllowed: Boolean, containerAvailable: Boolean, active: Boolean) {
        if (active) return
        _states.update { list ->
            list.map { channel ->
                when (channel.id) {
                    ChannelId.CONTAINER -> channel.copy(
                        status = if (containerAvailable) ChannelStatus.OK else ChannelStatus.BAD,
                        note = ChannelNote(
                            if (containerAvailable) ChannelNoteKind.RUNTIME_READY
                            else ChannelNoteKind.CONTAINER_UNAVAILABLE,
                        ),
                    )
                    ChannelId.GPS, ChannelId.NETWORK -> channel.copy(
                        note = ChannelNote(
                            if (mockAllowed) ChannelNoteKind.MOCK_STANDBY else ChannelNoteKind.NOT_MOCK_APP,
                        ),
                    )
                    ChannelId.CELL -> channel.copy(note = ChannelNote(ChannelNoteKind.FOLLOWS_CONTAINER))
                    else -> channel
                }
            }
        }
    }

    /** 停止会话后把运行态通道复位，避免界面继续显示"已下发"。 */
    fun resetToStopped() {
        _states.update { list ->
            list.map { channel ->
                when (channel.id) {
                    ChannelId.CONTAINER -> channel.copy(
                        status = ChannelStatus.OFF,
                        note = ChannelNote(ChannelNoteKind.STOPPED_CONTAINER),
                    )
                    ChannelId.GPS, ChannelId.NETWORK -> channel.copy(
                        status = ChannelStatus.OFF,
                        note = ChannelNote(ChannelNoteKind.STOPPED),
                    )
                    ChannelId.FUSED -> channel.copy(
                        status = ChannelStatus.WARN,
                        note = ChannelNote(ChannelNoteKind.FUSED_FORWARD_ONLY),
                    )
                    ChannelId.CELL -> channel.copy(status = ChannelStatus.OFF, note = ChannelNote(ChannelNoteKind.STOPPED))
                    else -> channel
                }
            }
        }
    }

    private fun update(id: ChannelId, transform: (ChannelState) -> ChannelState) {
        _states.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }
}
