package com.gpsproxy.dev.domain.simulation.port

import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils

/**
 * 一次定位写入的完整载荷。
 *
 * 两条通道此前各自接收 6~8 个位置参数（精度、海拔、速度、航向、卫星数、
 * 坐标系…），签名重复且在两个实现里各拼一遍 `Location` / `BLocation`。
 * 收敛成一个值对象后：
 * - 通道实现只负责「把同一份语义翻译成各自框架的对象」；
 * - 海拔与卫星数的推导只有一处，两个通道不会给出互相矛盾的值。
 *
 * [point] 始终是 GCJ-02 基准坐标（内部权威坐标），由通道按 [coordSystem] 转换。
 */
data class LocationSample(
    val point: GeoPoint,
    val coordSystem: CoordSystem,
    val accuracyMeters: Int,
    val altitude: Double,
    val speedMps: Float,
    val bearing: Float,
    val satellites: Int,
    val wallMillis: Long,
)

/**
 * 负责生成「看起来自然」的定位载荷。
 *
 * 海拔与卫星数带轻微抖动：完全恒定的值会被部分风控识别为伪造。
 * 抖动区间与旧实现保持一致（海拔 12±1m；运动 9–11 颗、静止 11–12 颗），
 * 以免改变既有观感。
 */
class LocationSampleFactory(private val random: com.gpsproxy.dev.core.random.RandomSource) {

    fun sample(
        point: GeoPoint,
        coordSystem: CoordSystem,
        accuracyMeters: Int,
        speedMps: Float,
        bearing: Float,
        wallMillis: Long,
        satellites: Int? = null,
        altitudeMeters: Double? = null,
    ): LocationSample {
        val baseAltitude = altitudeMeters ?: BASE_ALTITUDE
        val altitude = baseAltitude + (random.nextDouble() - 0.5) * ALTITUDE_JITTER
        val resolvedSatellites = satellites ?: if (speedMps > 0.5f) {
            SAT_SATELLITES_MOVING + random.nextInt(0, 3)
        } else {
            SAT_SATELLITES_STATIC + random.nextInt(0, 2)
        }
        return LocationSample(
            point = point,
            coordSystem = coordSystem,
            accuracyMeters = accuracyMeters,
            altitude = altitude,
            speedMps = speedMps,
            bearing = bearing,
            satellites = resolvedSatellites,
            wallMillis = wallMillis,
        )
    }

    /**
     * 静止点的自然漂移。
     *
     * 实现在圆内均匀分布（半径取 sqrt），与旧版 [GeoUtils.randomDrift] 完全同分布，
     * 但随机源可注入，便于断言范围与确定性测试。
     */
    fun drift(point: GeoPoint, radiusMeters: Double): GeoPoint {
        val angle = random.nextDouble() * Math.PI * 2
        val radius = radiusMeters * kotlin.math.sqrt(random.nextDouble())
        return GeoUtils.offsetMeters(
            point,
            kotlin.math.sin(angle) * radius,
            kotlin.math.cos(angle) * radius,
        )
    }

    companion object {
        /** 运动状态下的基准卫星数。 */
        const val SAT_SATELLITES_MOVING = 9

        /** 静止状态下的基准卫星数。 */
        const val SAT_SATELLITES_STATIC = 11

        private const val BASE_ALTITUDE = 12.0
        private const val ALTITUDE_JITTER = 2.0
    }
}
