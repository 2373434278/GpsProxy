package com.gpsproxy.dev.domain.simulation.port

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.result.Outcome
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import kotlinx.coroutines.flow.StateFlow

/**
 * 会话控制端口：用例只需要"改选点/停止"，不需要整个引擎。
 *
 * 拆出最小接口而不是直接依赖 [com.gpsproxy.dev.domain.simulation.SimulationEngine]：
 * 用例在单测里可以用两行 Fake 验证"是否调用了、按什么顺序调用"，
 * 不必装配通道、时钟、随机源等一整套依赖。
 */
interface SimulationControl {

    fun initialize(point: GeoPoint?, address: String)

    fun setPoint(point: GeoPoint, address: String)

    fun stop()

    fun nudge(northMeters: Double, eastMeters: Double)

    fun setBlurRadius(meters: Int)
}

/**
 * 会话启动端口：启动单点/轨迹并暴露只读状态。
 *
 * 与 [SimulationControl] 分开的原因：启动需要通道预检与异步规划，
 * 而选点/微调是同步操作，混在一个接口里会迫使测试 Fake 实现无关方法。
 */
interface SimulationStarter {

    val state: StateFlow<SimulationState>

    fun startSingle(): Outcome<Unit>

    suspend fun startRoute(config: RouteConfig): Outcome<Unit>

    /** 引擎已消费的最新设置快照；用例与引擎必须读同一份，避免启动瞬间的规则漂移。 */
    fun currentSettings(): com.gpsproxy.dev.domain.settings.AppSettings
}
