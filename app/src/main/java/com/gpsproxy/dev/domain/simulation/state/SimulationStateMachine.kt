package com.gpsproxy.dev.domain.simulation.state

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.domain.simulation.model.RouteProgress
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import com.gpsproxy.dev.domain.simulation.route.RouteSourceKind

/** 启动单点会话所需的、由引擎算好的值（模糊后的坐标、安装信息等）。 */
data class SingleStartPayload(
    val target: GeoPoint,
    val injected: GeoPoint,
    val blurRadiusMeters: Int,
    val accuracyMeters: Int,
    val installedProviders: List<String>,
    val startedAt: Long,
    val channel: com.gpsproxy.dev.domain.settings.InjectionChannel,
)

/** 启动轨迹会话所需的、由引擎算好的值。 */
data class RouteStartPayload(
    val config: RouteConfig,
    val target: GeoPoint,
    val injected: GeoPoint,
    val blurRadiusMeters: Int,
    val accuracyMeters: Int,
    val bearing: Float,
    val progress: RouteProgress,
    val installedProviders: List<String>,
    val startedAt: Long,
    val routeSource: RouteSourceKind?,
    val pathPoints: List<GeoPoint> = emptyList(),
    val degradeReason: String? = null,
    val channel: com.gpsproxy.dev.domain.settings.InjectionChannel,
)

/**
 * 会话生命周期命令。
 *
 * 注意这里只有**状态迁移**，不包含每帧的进度更新（那是连续量，不是"事件"）。
 */
sealed interface SimulationCommand {
    data class StartSingle(val payload: SingleStartPayload) : SimulationCommand
    data class StartRoute(val payload: RouteStartPayload) : SimulationCommand
    data object Pause : SimulationCommand
    data object Resume : SimulationCommand
    data object Stop : SimulationCommand
    data object Complete : SimulationCommand
    /** 轨迹完成后从头重播；只对 COMPLETED 生效。 */
    data object Restart : SimulationCommand
    data class Fail(val error: ErrorCode, val cause: String) : SimulationCommand
    /**
     * 启动阶段失败：会话从未真正运行，因此直接回到 IDLE + ERROR。
     * 与 [Fail] 的区别是后者用于运行中通道失效，需保留当前模式供诊断展示。
     */
    data class Abort(val error: ErrorCode, val cause: String) : SimulationCommand
}

/** 非法迁移的结构化原因；文案在需要展示时再按当前语言解析。 */
enum class RejectionReason {
    ALREADY_RUNNING,
    NO_ACTIVE_SESSION,
    NOT_RUNNING,
    NOT_PAUSED,
    ROUTE_ONLY_COMPLETE,
    ROUTE_ONLY_REPLAY,
    COMPLETED_ONLY,
}

sealed interface SimulationTransition {
    data class Accepted(val state: SimulationState) : SimulationTransition
    data class Rejected(val reason: RejectionReason) : SimulationTransition
}

/**
 * 会话状态机（纯函数）。
 *
 * 旧实现靠调用顺序隐式保证合法性：`startSingle` 在运行中再次调用会把计时器
 * 重建、`resume` 在非暂停态调用会被悄悄忽略、`stop` 与 `pause` 的竞态只能靠
 * 阅读所有分支才能确认。把这些规则显式化之后：
 * - 非法命令返回 [SimulationTransition.Rejected]，不会产生中间态；
 * - 规则本身可以在毫秒级单测里穷举；
 * - UI 的按钮可用性可以由同一套规则推导，不再各写一遍判断。
 *
 * 投影字段（进度、计数、通道状态）由引擎在帧循环里直接写入快照，
 * 不属于"迁移"，因此这里不处理。
 */
class SimulationStateMachine(
    private val strings: () -> Strings = { StringsHolder.current },
) {

    fun reduce(current: SimulationState, command: SimulationCommand): SimulationTransition =
        when (command) {
            is SimulationCommand.StartSingle -> {
                if (isActive(current)) {
                    SimulationTransition.Rejected(RejectionReason.ALREADY_RUNNING)
                } else {
                    val p = command.payload
                    SimulationTransition.Accepted(
                        current.copy(
                            mode = SimMode.SINGLE,
                            runState = RunState.RUNNING,
                            point = p.target,
                            injectedPoint = p.injected,
                            blurRadiusMeters = p.blurRadiusMeters,
                            accuracy = p.accuracyMeters,
                            installedProviders = p.installedProviders,
                            startedAt = p.startedAt,
                            speedKmh = 0.0,
                            speedFactor = 1f,
                            walking = false,
                            autoStopAtMillis = 0L,
                            route = null,
                            routeSource = null,
                            routeDegradeReason = null,
                            progress = null,
                            errorCode = null,
                        )
                    )
                }
            }

            is SimulationCommand.StartRoute -> {
                if (isActive(current)) {
                    SimulationTransition.Rejected(RejectionReason.ALREADY_RUNNING)
                } else {
                    val p = command.payload
                    SimulationTransition.Accepted(
                        current.copy(
                            mode = SimMode.ROUTE,
                            runState = RunState.RUNNING,
                            point = p.target,
                            injectedPoint = p.injected,
                            blurRadiusMeters = p.blurRadiusMeters,
                            accuracy = p.accuracyMeters,
                            address = routeLabel(p),
                            speedKmh = p.config.speedKmh.toDouble(),
                            speedFactor = 1f,
                            walking = false,
                            autoStopAtMillis = 0L,
                            bearing = p.bearing,
                            route = p.config,
                            routeSource = p.routeSource,
                            routePathPoints = p.pathPoints,
                            routeDegradeReason = p.degradeReason,
                            progress = p.progress,
                            installedProviders = p.installedProviders,
                            startedAt = p.startedAt,
                            errorCode = null,
                        )
                    )
                }
            }

            SimulationCommand.Pause -> when {
                current.mode == SimMode.IDLE -> SimulationTransition.Rejected(RejectionReason.NO_ACTIVE_SESSION)
                current.runState != RunState.RUNNING -> SimulationTransition.Rejected(RejectionReason.NOT_RUNNING)
                else -> SimulationTransition.Accepted(
                    current.copy(runState = RunState.PAUSED, speedKmh = 0.0, walking = false)
                )
            }

            SimulationCommand.Resume -> when {
                current.runState != RunState.PAUSED -> SimulationTransition.Rejected(RejectionReason.NOT_PAUSED)
                else -> SimulationTransition.Accepted(
                    current.copy(
                        runState = RunState.RUNNING,
                        walking = false,
                        speedKmh = if (current.mode == SimMode.ROUTE) {
                            (current.route?.speedKmh ?: 0).toDouble() * current.speedFactor
                        } else {
                            0.0
                        },
                    )
                )
            }

            SimulationCommand.Stop -> SimulationTransition.Accepted(
                current.copy(
                    mode = SimMode.IDLE,
                    runState = RunState.IDLE,
                    speedKmh = 0.0,
                    speedFactor = 1f,
                    walking = false,
                    autoStopAtMillis = 0L,
                    route = null,
                    routeSource = null,
                    routePathPoints = emptyList(),
                    routeDegradeReason = null,
                    progress = null,
                    injectedPoint = null,
                    blurRadiusMeters = 0,
                    installedProviders = emptyList(),
                    errorCode = null,
                    injectionChannel = null,
                )
            )

            SimulationCommand.Complete -> when {
                current.mode != SimMode.ROUTE -> SimulationTransition.Rejected(RejectionReason.ROUTE_ONLY_COMPLETE)
                current.runState != RunState.RUNNING && current.runState != RunState.PAUSED ->
                    SimulationTransition.Rejected(RejectionReason.NOT_RUNNING)
                else -> SimulationTransition.Accepted(
                    current.copy(
                        runState = RunState.COMPLETED,
                        speedKmh = 0.0,
                        walking = false,
                        autoStopAtMillis = 0L,
                    )
                )
            }

            SimulationCommand.Restart -> when {
                current.mode != SimMode.ROUTE -> SimulationTransition.Rejected(RejectionReason.ROUTE_ONLY_REPLAY)
                current.runState != RunState.COMPLETED -> SimulationTransition.Rejected(RejectionReason.COMPLETED_ONLY)
                else -> {
                    val total = current.progress?.distanceTotalM ?: 0.0
                    val effectiveSpeed = (current.route?.speedKmh ?: 0).toDouble() * current.speedFactor
                    SimulationTransition.Accepted(
                        current.copy(
                            runState = RunState.RUNNING,
                            speedKmh = effectiveSpeed,
                            walking = false,
                            autoStopAtMillis = 0L,
                            errorCode = null,
                            progress = current.progress?.copy(
                                fraction = 0f,
                                distanceDoneM = 0.0,
                                remainingSeconds = if (effectiveSpeed > 0) {
                                    (total / (effectiveSpeed / 3.6)).toInt().coerceAtLeast(0)
                                } else {
                                    0
                                },
                                pointIndex = 0,
                            ),
                        )
                    )
                }
            }

            is SimulationCommand.Fail -> SimulationTransition.Accepted(
                current.copy(
                    runState = RunState.ERROR,
                    errorCode = command.error,
                    speedKmh = 0.0,
                    walking = false,
                    autoStopAtMillis = 0L,
                )
            )

            is SimulationCommand.Abort -> SimulationTransition.Accepted(
                current.copy(
                    mode = SimMode.IDLE,
                    runState = RunState.ERROR,
                    errorCode = command.error,
                    speedKmh = 0.0,
                    speedFactor = 1f,
                    walking = false,
                    autoStopAtMillis = 0L,
                    route = null,
                    routeSource = null,
                    routePathPoints = emptyList(),
                    routeDegradeReason = null,
                    progress = null,
                    installedProviders = emptyList(),
                    injectionChannel = null,
                )
            )
        }

    private fun routeLabel(payload: RouteStartPayload): String {
        val route = strings().route
        return when (payload.config.kind) {
            RouteKind.ROAM -> route.routeAddressRoam(payload.config.roamRadiusMeters)
            RouteKind.IMPORTED -> route.routeAddressImported(payload.pathPoints.size)
            RouteKind.POINT_TO_POINT ->
                route.routeAddressPointToPoint(route.travelModeLabel(payload.config.mode.name))
        }
    }

    private fun isActive(state: SimulationState): Boolean =
        state.mode != SimMode.IDLE &&
            (state.runState == RunState.RUNNING || state.runState == RunState.PAUSED)
}
