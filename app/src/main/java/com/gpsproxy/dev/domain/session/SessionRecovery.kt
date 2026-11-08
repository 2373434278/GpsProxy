package com.gpsproxy.dev.domain.session

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.domain.simulation.model.SimulationState

/**
 * 可恢复的会话快照（进程重启/开机后判断是否需要提醒用户）。
 *
 * 只保存恢复所需的最小信息：上次是否在运行、运行在哪、模式与开始时间。
 * 不保存通道状态、通道计数等运行时数据，避免产生"恢复了一半"的错觉。
 */
data class RecoverableSession(
    val wasActive: Boolean,
    val mode: SimMode?,
    val point: GeoPoint?,
    val address: String,
    val startedAt: Long,
)

/**
 * 会话录制端口。
 *
 * 领域引擎在启动/停止时通知实现方持久化状态；实现可以是 DataStore，
 * 也可以是测试里的内存记录器。引擎不直接依赖任何存储设施。
 */
interface SessionRecorder {

    suspend fun onStarted(state: SimulationState)

    suspend fun onStopped()

    companion object {
        /** 默认空实现：不录制（测试或无持久化需求的场景）。 */
        val Noop: SessionRecorder = object : SessionRecorder {
            override suspend fun onStarted(state: SimulationState) = Unit
            override suspend fun onStopped() = Unit
        }
    }
}
