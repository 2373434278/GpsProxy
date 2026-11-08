package com.gpsproxy.dev.domain.simulation.blur

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils

/**
 * 位置模糊策略。
 *
 * 目标：应用读到的是真实选点附近的另一个点，且**同一次会话内偏移恒定**。
 * 若每个点各自随机：
 * - 手动微调时标记会往随机方向跳，用户按「向北」却看到偏南；
 * - 轨迹会被打散成噪点，形状与走向全丢；
 * - 观察者可以通过多点采样反推出真实原点。
 *
 * 因此实现必须满足两条不变量（单测覆盖）：
 * 1. `注入点 == 真实点 + 固定偏移`，偏移模长 ≤ 半径；
 * 2. 半径与锚点确定时，多次调用/跨会话结果一致。
 */
interface BlurPolicy {

    /** 生效半径（米），0 表示未开启。 */
    val radiusMeters: Int

    /** 真实选点 → 实际注入点。 */
    fun injectedPoint(anchor: GeoPoint): GeoPoint

    /** 把整条轨迹按同一个偏移整体平移（逐点各自模糊会破坏形状）。 */
    fun translate(points: List<GeoPoint>): List<GeoPoint>

    companion object {
        /** 按半径选择实现；半径 ≤ 0 使用空策略，调用方无需分支。 */
        fun of(radiusMeters: Int): BlurPolicy =
            if (radiusMeters <= 0) NoBlurPolicy else DeterministicBlurPolicy(radiusMeters)
    }
}

/** 空对象：不改变任何坐标。 */
object NoBlurPolicy : BlurPolicy {

    override val radiusMeters: Int = 0

    override fun injectedPoint(anchor: GeoPoint): GeoPoint = anchor

    override fun translate(points: List<GeoPoint>): List<GeoPoint> = points
}

/**
 * 确定性模糊：偏移由「锚点 + 半径」推导，缓存后整场会话复用。
 *
 * 注意缓存只按半径命中、不因锚点变化而失效——这是旧实现的既定语义：
 * 用户连续微调时复用同一偏移，方向感才与操作一致。
 * 因为推导本身是确定性的，即使缓存被清空，同锚点同半径仍会得到同一偏移。
 */
class DeterministicBlurPolicy(override val radiusMeters: Int) : BlurPolicy {

    private data class BlurOffset(val radiusMeters: Int, val north: Double, val east: Double)

    private var cached: BlurOffset? = null

    override fun injectedPoint(anchor: GeoPoint): GeoPoint {
        if (radiusMeters <= 0) return anchor
        val offset = offsetFor(anchor)
        return GeoUtils.offsetMeters(anchor, offset.north, offset.east)
    }

    override fun translate(points: List<GeoPoint>): List<GeoPoint> {
        if (radiusMeters <= 0 || points.isEmpty()) return points
        val offset = offsetFor(points.first())
        if (offset.north == 0.0 && offset.east == 0.0) return points
        return points.map { GeoUtils.offsetMeters(it, offset.north, offset.east) }
    }

    private fun offsetFor(anchor: GeoPoint): BlurOffset {
        val current = cached
        if (current != null && current.radiusMeters == radiusMeters) return current
        val (north, east) = GeoUtils.blurOffsetMeters(anchor, radiusMeters.toDouble())
        return BlurOffset(radiusMeters, north, east).also { cached = it }
    }
}
