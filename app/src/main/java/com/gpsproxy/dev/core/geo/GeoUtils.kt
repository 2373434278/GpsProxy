package com.gpsproxy.dev.core.geo

import com.gpsproxy.dev.core.i18n.text.RouteTexts
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class PathPosition(
    val point: GeoPoint,
    val bearing: Float,
    val distanceAlong: Double,
    val pointIndex: Int,
)

class RoutePath(val points: List<GeoPoint>) {
    val cumulative: DoubleArray = DoubleArray(points.size)
    val totalDistance: Double
    val segmentBearings: FloatArray = FloatArray(max(0, points.size - 1))

    init {
        var sum = 0.0
        for (i in points.indices) {
            if (i > 0) {
                sum += GeoUtils.distanceMeters(points[i - 1], points[i])
                segmentBearings[i - 1] = GeoUtils.bearingDegrees(points[i - 1], points[i])
            }
            cumulative[i] = sum
        }
        totalDistance = sum
    }

    fun positionAt(distance: Double): PathPosition? {
        if (points.isEmpty()) return null
        if (points.size == 1) return PathPosition(points[0], 0f, 0.0, 0)
        val target = distance.coerceIn(0.0, totalDistance)
        var low = 0
        var high = cumulative.lastIndex
        while (low < high) {
            val mid = (low + high) / 2
            if (cumulative[mid] < target) low = mid + 1 else high = mid
        }
        val endIndex = low.coerceAtLeast(1)
        val startIndex = endIndex - 1
        val segment = cumulative[endIndex] - cumulative[startIndex]
        val t = if (segment <= 0.0) 0.0 else ((target - cumulative[startIndex]) / segment).coerceIn(0.0, 1.0)
        val a = points[startIndex]
        val b = points[endIndex]
        val point = GeoPoint(
            lat = a.lat + (b.lat - a.lat) * t,
            lng = a.lng + (b.lng - a.lng) * t,
        )
        return PathPosition(
            point = point,
            bearing = segmentBearings[startIndex],
            distanceAlong = target,
            pointIndex = endIndex,
        )
    }
}

object GeoUtils {

    private const val EARTH_RADIUS_M = 6371008.8

    /**
     * 每纬度对应的米数。
     *
     * 旧实现在 [offsetMeters]/[projectMeters] 里用 111320，而 [distanceMeters]
     * 用真实地球半径推导出 111194.93，导致「偏移 100m」实际只有 99.89m，
     * 往返换算会累积误差。这里统一到同一常量。
     */
    private const val METERS_PER_DEGREE = EARTH_RADIUS_M * Math.PI / 180.0

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    fun bearingDegrees(a: GeoPoint, b: GeoPoint): Float {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val deg = Math.toDegrees(atan2(y, x)).toFloat()
        return (deg + 360f) % 360f
    }

    /** Offset a point by meters; north positive, east positive. */
    fun offsetMeters(point: GeoPoint, northMeters: Double, eastMeters: Double): GeoPoint {
        val lat = point.lat + northMeters / METERS_PER_DEGREE
        val lng = point.lng + eastMeters / (METERS_PER_DEGREE * cos(Math.toRadians(point.lat)).coerceAtLeast(0.01))
        return GeoPoint(lat, lng)
    }



    /**
     * 与位置模糊同一套确定性随机：返回「北向 / 东向」偏移量（米）。
     *
     * 轨迹模式下需要把整条轨迹按同一个偏移整体平移，否则每个点各自模糊会让
     * 路线变成抖动噪点，所以这里把偏移量单独暴露出来复用。
     */
    fun blurOffsetMeters(point: GeoPoint, radiusMeters: Double): Pair<Double, Double> {
        if (radiusMeters <= 0.0) return 0.0 to 0.0
        val seed = listOf(point.lat, point.lng, radiusMeters)
            .joinToString("|") { String.format(java.util.Locale.US, "%.7f", it) }
            .hashCode()
        val rng = java.util.Random(seed.toLong())
        val angle = rng.nextDouble() * Math.PI * 2
        val radius = radiusMeters * sqrt(rng.nextDouble())
        return sin(angle) * radius to cos(angle) * radius
    }

    fun projectMeters(origin: GeoPoint, point: GeoPoint): Pair<Double, Double> {
        val north = (point.lat - origin.lat) * METERS_PER_DEGREE
        val east = (point.lng - origin.lng) * METERS_PER_DEGREE * cos(Math.toRadians(origin.lat))
        return north to east
    }

    fun unprojectMeters(origin: GeoPoint, north: Double, east: Double): GeoPoint {
        val lat = origin.lat + north / METERS_PER_DEGREE
        val lng = origin.lng + east / (METERS_PER_DEGREE * cos(Math.toRadians(origin.lat)).coerceAtLeast(0.01))
        return GeoPoint(lat, lng)
    }

    /**
     * Synthetic road-like curve between two points (quadratic Bezier with a perpendicular
     * bulge). Used when no routing service key is configured; every injected point lies exactly
     * on this polyline, so the simulated trace never deviates from its own plan.
     */
    fun buildSyntheticRoute(start: GeoPoint, end: GeoPoint, samples: Int = 0): List<GeoPoint> {
        val direct = distanceMeters(start, end)
        if (direct < 1.0) return listOf(start, end)
        val count = if (samples > 0) samples else (direct / 5.0).toInt().coerceIn(24, 600)
        val mid = GeoPoint((start.lat + end.lat) / 2, (start.lng + end.lng) / 2)
        val startLocal = projectMeters(mid, start)
        val endLocal = projectMeters(mid, end)
        val dx = endLocal.second - startLocal.second
        val dy = endLocal.first - startLocal.first
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)
        val bulge = length * 0.12
        val control = Pair(
            (startLocal.first + endLocal.first) / 2 + (-dx / length) * bulge,
            (startLocal.second + endLocal.second) / 2 + (dy / length) * bulge,
        )
        val result = ArrayList<GeoPoint>(count + 1)
        for (i in 0..count) {
            val t = i.toDouble() / count
            val oneMinus = 1 - t
            val n = oneMinus * oneMinus * startLocal.first + 2 * oneMinus * t * control.first + t * t * endLocal.first
            val e = oneMinus * oneMinus * startLocal.second + 2 * oneMinus * t * control.second + t * t * endLocal.second
            result.add(unprojectMeters(mid, n, e))
        }
        return result
    }

    /**
     * 多段合成轨迹：起点 → 途经点… → 终点。
     *
     * 每段复用 [buildSyntheticRoute]，只在接缝处去掉重复的首点，
     * 保证结果是一条连续折线而不是若干段独立曲线的堆叠。
     */
    fun buildSyntheticMultiRoute(stops: List<GeoPoint>): List<GeoPoint> {
        if (stops.size < 2) return stops
        val result = ArrayList<GeoPoint>()
        for (index in 0 until stops.size - 1) {
            val segment = buildSyntheticRoute(stops[index], stops[index + 1])
            if (segment.isEmpty()) continue
            if (result.isEmpty()) result.addAll(segment) else result.addAll(segment.drop(1))
        }
        return result
    }

    /**
     * 等间隔降采样（保留首尾）。
     *
     * 导入的 GPX 可能有上万点：仿真侧用完整轨迹，地图绘制与路线库持久化
     * 只需要形状，降采样后显著降低单帧绘制与 DataStore 体积。
     */
    fun downsample(points: List<GeoPoint>, maxPoints: Int): List<GeoPoint> {
        if (maxPoints <= 2 || points.size <= maxPoints) return points
        val step = (points.size - 1).toDouble() / (maxPoints - 1)
        val sampled = ArrayList<GeoPoint>(maxPoints)
        for (index in 0 until maxPoints) {
            val at = (index * step).toInt().coerceIn(0, points.lastIndex)
            sampled.add(points[at])
        }
        sampled[0] = points.first()
        sampled[sampled.lastIndex] = points.last()
        return sampled
    }

    /**
     * 以 [center] 为圆心的闭合圆环（首尾同点），用于漫游范围的示意预览。
     *
     * 只是可视化辅助，不参与真实漫游规划；真实漫游的随机环线由
     * `RoamPlanner` 生成。
     */
    fun circlePath(center: GeoPoint, radiusMeters: Double, segments: Int = 48): List<GeoPoint> {
        if (radiusMeters <= 0.0) return listOf(center)
        val count = segments.coerceIn(8, 256)
        return (0..count).map { index ->
            val angle = 2.0 * Math.PI * index / count
            offsetMeters(
                center,
                northMeters = cos(angle) * radiusMeters,
                eastMeters = sin(angle) * radiusMeters,
            )
        }
    }

    fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.toInt()} m"
        else -> String.format(java.util.Locale.US, "%.1f km", meters / 1000.0)
    }

    fun formatDuration(seconds: Int, texts: RouteTexts): String = texts.duration(seconds)
}
