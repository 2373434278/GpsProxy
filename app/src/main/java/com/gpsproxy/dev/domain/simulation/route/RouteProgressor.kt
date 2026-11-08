package com.gpsproxy.dev.domain.simulation.route

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.RoutePath
import com.gpsproxy.dev.domain.simulation.blur.BlurPolicy
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteProgress

/**
 * 一次巡航的可变运行时。
 *
 * 它是**引擎私有**状态：外部只能通过 [com.gpsproxy.dev.domain.simulation.model.SimulationState.progress]
 * 读取投影，不能直接改写已走距离。把可变部分集中在一个小对象里，避免旧实现中
 * 6 个散落字段（path/basePath/travelled/lastBearing…）被任何方法顺手修改。
 *
 * [speedFactor] 与 [direction] 是"运行中控制"的落点：变速不重建轨迹，
 * 反向不反转折线，只改变推进的符号与倍率，因此进度、暂停、模糊语义都保持不变。
 */
class RouteRuntime(
    var config: RouteConfig,
    val basePath: RoutePath,
    var path: RoutePath,
    var travelledMeters: Double = 0.0,
    var lastBearing: Float = 0f,
    var speedFactor: Float = 1f,
    var direction: Int = 1,
) {
    /** 本次巡航的总里程（往返模式为单程的两倍）。 */
    val fullDistanceMeters: Double
        get() = if (config.roundTrip) path.totalDistance * 2 else path.totalDistance

    /** 叠加倍速后的实际推进速度。 */
    val effectiveSpeedMps: Double
        get() = config.speedMps * speedFactor
}

/**
 * 单帧推进结果。
 *
 * @param targetPoint 用户语义上的目标点（未模糊），用于展示与收藏
 * @param injectedPoint 实际写入通道的坐标（已模糊）
 * @param completed 是否到达终点且不再继续
 */
data class RouteAdvance(
    val targetPoint: GeoPoint,
    val injectedPoint: GeoPoint,
    val bearing: Float,
    val speedMps: Float,
    val progress: RouteProgress,
    val completed: Boolean,
)

/**
 * 轨迹推进器（纯逻辑）。
 *
 * 旧实现把「时间差推进、循环/往返/单程终点处理、进度计算、航向反转」
 * 全塞在控制器的一个方法里，且依赖可变的成员字段。抽成纯类后，
 * 上述三种终点行为都能用毫秒级输入直接断言。
 *
 * 关键不变量：
 * - 往返模式下越过单程终点后沿原路返回，航向 +180°；
 * - 循环模式到达终点后回到起点继续，不产生"完成"；
 * - 单程模式到达终点后 `completed = true`，速度归零；
 * - 进度分母始终是**本次会话总里程**（往返为双程）；
 * - 反向只改变推进符号：到起点后完成，循环模式则回绕到终点。
 */
class RouteProgressor {

    /** 用原始轨迹与模糊策略创建运行时；模糊只平移、不改变形状。 */
    fun createRuntime(
        config: RouteConfig,
        basePath: RoutePath,
        blur: BlurPolicy,
    ): RouteRuntime {
        val path = if (blur.radiusMeters > 0) RoutePath(blur.translate(basePath.points)) else basePath
        return RouteRuntime(
            config = config,
            basePath = basePath,
            path = path,
            lastBearing = path.segmentBearings.firstOrNull() ?: 0f,
        )
    }

    /**
     * 推进一帧。
     *
     * 用「实际流逝时间」而不是固定步长：一次 GC 或系统卡顿后固定步长会让
     * 模拟位置落后于真实时间，出现明显变速。
     *
     * @return 轨迹长度为 0 时返回 null，由调用方按 ROUTE-0302 处理
     */
    fun advance(runtime: RouteRuntime, elapsedMillis: Long): RouteAdvance? {
        val total = runtime.fullDistanceMeters
        if (total <= 0.0 || runtime.path.points.isEmpty()) return null

        val delta = runtime.effectiveSpeedMps *
            (elapsedMillis.coerceAtLeast(1L) / 1000.0) *
            runtime.direction
        runtime.travelledMeters += delta

        var completed = false
        if (runtime.direction >= 0) {
            if (runtime.travelledMeters >= total) {
                if (runtime.config.loop) {
                    runtime.travelledMeters = 0.0
                } else {
                    runtime.travelledMeters = total
                    completed = true
                }
            }
        } else {
            if (runtime.travelledMeters <= 0.0) {
                if (runtime.config.loop) {
                    runtime.travelledMeters = total
                } else {
                    runtime.travelledMeters = 0.0
                    completed = true
                }
            }
        }
        return buildAdvance(runtime, completed)
    }

    /**
     * 跳转到轨迹的指定比例位置。
     *
     * 只更新运行时与返回一帧投影，不修改会话状态；调用方决定是否推送与是否保持暂停。
     * 钳到 0.999 而不是 1.0：单程终点由帧循环自然完成，避免"跳转即完成"的突兀语义。
     */
    fun seek(runtime: RouteRuntime, fraction: Float): RouteAdvance? {
        val total = runtime.fullDistanceMeters
        if (total <= 0.0 || runtime.path.points.isEmpty()) return null
        runtime.travelledMeters = total * fraction.coerceIn(0f, 0.999f)
        return buildAdvance(runtime, completed = false)
    }

    /** 归零并恢复正向，用于"重播"。 */
    fun reset(runtime: RouteRuntime) {
        runtime.travelledMeters = 0.0
        runtime.direction = 1
        runtime.lastBearing = runtime.path.segmentBearings.firstOrNull() ?: 0f
    }

    /**
     * 剩余时间（秒）。
     *
     * 反向推进时剩余的是"回到起点"的时间；倍速通过 [RouteRuntime.effectiveSpeedMps]
     * 自动生效，因此变速后进度卡片无需重建轨迹即可刷新。
     */
    fun remainingSeconds(runtime: RouteRuntime): Int {
        val total = runtime.fullDistanceMeters
        val travelled = runtime.travelledMeters.coerceIn(0.0, total)
        val speed = runtime.effectiveSpeedMps
        if (speed <= 0.0) return 0
        return if (runtime.direction >= 0) {
            ((total - travelled) / speed).toInt().coerceAtLeast(0)
        } else {
            (travelled / speed).toInt().coerceAtLeast(0)
        }
    }

    /** 运行中模糊半径变化后重建平移轨迹，已走距离保持不变。 */
    fun rebindBlur(runtime: RouteRuntime, blur: BlurPolicy) {
        runtime.path = if (blur.radiusMeters > 0) {
            RoutePath(blur.translate(runtime.basePath.points))
        } else {
            runtime.basePath
        }
    }

    private fun buildAdvance(runtime: RouteRuntime, completed: Boolean): RouteAdvance? {
        val path = runtime.path
        val route = runtime.config
        val total = runtime.fullDistanceMeters
        val single = path.totalDistance
        val travelled = runtime.travelledMeters.coerceIn(0.0, total)

        // 往返模式下 travelled 映射到单程折线：前半程正向，后半程反向。
        val along: Double
        val onReturnLeg: Boolean
        if (route.roundTrip && single > 0.0) {
            val cycle = single * 2.0
            val phase = travelled % cycle
            onReturnLeg = phase > single
            along = if (onReturnLeg) cycle - phase else phase
        } else {
            onReturnLeg = false
            along = travelled.coerceAtMost(single)
        }

        val position = path.positionAt(along.coerceAtLeast(0.0)) ?: return null
        val back = onReturnLeg != (runtime.direction < 0)
        val bearing = if (back) (position.bearing + 180f) % 360f else position.bearing
        runtime.lastBearing = bearing
        val targetPoint = runtime.basePath.positionAt(along.coerceAtLeast(0.0))?.point ?: position.point
        val fraction = if (total > 0.0) (travelled / total).coerceIn(0.0, 1.0).toFloat() else 0f
        val speed = runtime.effectiveSpeedMps
        return RouteAdvance(
            targetPoint = targetPoint,
            injectedPoint = position.point,
            bearing = bearing,
            speedMps = if (completed) 0f else speed.toFloat(),
            progress = RouteProgress(
                fraction = fraction,
                distanceDoneM = travelled,
                distanceTotalM = total,
                remainingSeconds = remainingSeconds(runtime),
                pointIndex = position.pointIndex,
                totalPoints = path.points.size,
            ),
            completed = completed,
        )
    }
}
