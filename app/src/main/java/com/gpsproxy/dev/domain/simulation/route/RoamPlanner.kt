package com.gpsproxy.dev.domain.simulation.route

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.core.random.RandomSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 随机漫游参数：以 [anchor] 为圆心，在 [radiusMeters] 内生成一条闭合环线。 */
data class RoamConfig(
    val anchor: GeoPoint,
    val radiusMeters: Int = 500,
    val speedKmh: Int = 5,
    val vertices: Int = 12,
)

/**
 * 随机漫游规划器。
 *
 * 设计取舍：不做"每帧随机转向"的自由漫游，而是**每次会话生成一条闭合环线**，
 * 之后交给普通轨迹管线循环播放。好处是进度、倍速、跳转、反向、导出、
 * 路线收藏全部免费复用；代价是同一会话每圈路线相同——界面对此如实说明，
 * 不把它伪装成每次都不一样的随机游走。
 *
 * 顶点按角度递增生成并排序，形成围绕锚点的简单多边形；顶点数、半径与角度
 * 抖动都受控，保证不会退化成穿心折线。
 */
class RoamPlanner(private val random: RandomSource) {

    fun plan(config: RoamConfig): RoutePlan {
        val radius = config.radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS).toDouble()
        val vertices = config.vertices.coerceIn(MIN_VERTICES, MAX_VERTICES)
        val points = ArrayList<GeoPoint>(vertices + 2)
        points.add(config.anchor)
        val step = 2.0 * PI / vertices
        for (index in 0 until vertices) {
            // 抖动不超过半个扇区，角度单调，顶点顺序天然是绕行顺序。
            val jitter = (random.nextDouble() - 0.5) * step * 0.6
            val angle = index * step + jitter
            val distance = radius * (MIN_RADIUS_RATIO + (1.0 - MIN_RADIUS_RATIO) * random.nextDouble())
            points.add(
                GeoUtils.offsetMeters(
                    config.anchor,
                    northMeters = cos(angle) * distance,
                    eastMeters = sin(angle) * distance,
                ),
            )
        }
        points.add(config.anchor)
        return RoutePlan(points = points, source = RouteSourceKind.ROAM)
    }

    companion object {
        const val MIN_RADIUS_METERS = 50
        const val MAX_RADIUS_METERS = 5_000
        const val MIN_VERTICES = 8
        const val MAX_VERTICES = 16
        private const val MIN_RADIUS_RATIO = 0.45
    }
}
