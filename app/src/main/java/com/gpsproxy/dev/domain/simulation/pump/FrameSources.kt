package com.gpsproxy.dev.domain.simulation.pump

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.simulation.model.MotionVector
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import com.gpsproxy.dev.domain.simulation.port.LocationSampleFactory
import com.gpsproxy.dev.domain.simulation.route.RouteAdvance
import com.gpsproxy.dev.domain.simulation.route.RouteProgressor
import com.gpsproxy.dev.domain.simulation.route.RouteRuntime
import kotlin.math.cos
import kotlin.math.sin

/**
 * 单点模式的帧来源。
 *
 * 两种子行为：
 * - **静止**：基准取模糊后的注入坐标，漂移只在模糊结果之上叠加很小的抖动，
 *   不会把两者叠加成更大的偏移；上报速度/航向来自设置，用于测试显示速度的应用。
 * - **行走**（摇杆按住）：按 `速度 × 实际流逝时间` 沿方位角推进目标点，
 *   再交给引擎做模糊与状态投影；行走时不做静止漂移。
 *
 * 行走帧间隔降到 250ms：1–2 秒一帧会让"按住走"变成跳跃式位移。
 */
class SinglePointFrameSource(
    private val state: () -> SimulationState,
    private val settings: () -> AppSettings,
    private val samples: LocationSampleFactory,
    private val publish: (GeoPoint, Float, Float) -> PublishOutcome,
    private val bearing: () -> Float,
    private val failureError: () -> ErrorCode,
    private val motion: () -> MotionVector? = { null },
    /** 收到推进后的新目标点，返回实际注入点；返回 null 表示拒绝该步（例如越界）。 */
    private val onMoved: (GeoPoint, Float, Float) -> GeoPoint? = { point, _, _ -> point },
) : FrameSource {

    override fun intervalMillis(): Long = if (motion() != null) {
        WALK_INTERVAL_MS
    } else {
        settings().updateIntervalMs.toLong().coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    }

    override suspend fun onFrame(elapsedMillis: Long): FrameOutcome {
        val snapshot = state()
        val manual = motion()
        if (manual != null && snapshot.mode == SimMode.SINGLE && snapshot.runState == RunState.RUNNING) {
            val base = snapshot.point ?: return FrameOutcome.Continue
            val distance = manual.speedMps * (elapsedMillis.coerceAtLeast(1L) / 1000.0)
            val radians = Math.toRadians(manual.normalizedBearing.toDouble())
            val next = GeoUtils.offsetMeters(
                base,
                northMeters = cos(radians) * distance,
                eastMeters = sin(radians) * distance,
            )
            val injected = onMoved(next, manual.normalizedBearing, manual.speedMps)
                ?: return FrameOutcome.Continue
            return when (publish(injected, manual.speedMps, manual.normalizedBearing)) {
                PublishOutcome.FAILED -> FrameOutcome.Failed(failureError())
                else -> FrameOutcome.Continue
            }
        }

        val base = snapshot.injectedPoint ?: snapshot.point ?: return FrameOutcome.Continue
        val drifted = if (settings().driftEnabled) samples.drift(base, DRIFT_RADIUS_METERS) else base
        val reportSpeed = settings().reportSpeedKmh / 3.6f
        val reportBearing = if (settings().reportBearingDeg == 0) {
            bearing()
        } else {
            settings().reportBearingDeg.toFloat()
        }
        return when (publish(drifted, reportSpeed, reportBearing)) {
            PublishOutcome.FAILED -> FrameOutcome.Failed(failureError())
            else -> FrameOutcome.Continue
        }
    }

    private companion object {
        /** 静止点的自然漂移半径（米），与旧实现一致。 */
        const val DRIFT_RADIUS_METERS = 1.25

        const val MIN_INTERVAL_MS = 500L
        const val MAX_INTERVAL_MS = 2000L

        /** 行走时的帧间隔：跟手优先。 */
        const val WALK_INTERVAL_MS = 250L
    }
}

/**
 * 轨迹模式的帧来源。
 *
 * 间隔固定 250ms（播放流畅度优先），推进量由真实流逝时间决定；
 * 到达终点的三种行为（循环 / 往返 / 单程）由 [RouteProgressor] 决定，
 * 这里只负责把结果转成状态更新与通道写入。
 */
class RouteFrameSource(
    private val runtime: RouteRuntime,
    private val progressor: RouteProgressor,
    private val onAdvance: (RouteAdvance) -> Unit,
    private val onCompleted: () -> Unit,
    private val publish: (GeoPoint, Float, Float) -> PublishOutcome,
    private val failureError: () -> ErrorCode,
) : FrameSource {

    override fun intervalMillis(): Long = ROUTE_INTERVAL_MS

    override suspend fun onFrame(elapsedMillis: Long): FrameOutcome {
        val advance = progressor.advance(runtime, elapsedMillis)
            ?: return FrameOutcome.Failed(ErrorCode.ROUTE_0302)
        onAdvance(advance)
        val outcome = publish(advance.injectedPoint, advance.speedMps, advance.bearing)
        if (outcome == PublishOutcome.FAILED) return FrameOutcome.Failed(failureError())
        if (advance.completed) {
            onCompleted()
            return FrameOutcome.Completed
        }
        return FrameOutcome.Continue
    }

    private companion object {
        const val ROUTE_INTERVAL_MS = 250L
    }
}
