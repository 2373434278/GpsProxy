package com.gpsproxy.dev.core.geo

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/** 平面坐标（Web Mercator 世界坐标或屏幕像素坐标，具体含义由调用方决定）。 */
data class PlanarPoint(val x: Double, val y: Double)

/**
 * Web Mercator 投影与屏幕坐标换算。
 *
 * 这些公式原本内联在 400+ 行的 `SchematicMap` Composable 里，属于纯数学，
 * 却因为和 Compose 的 `Offset` 绑在一起而无法单测。抽出来后：
 * - 地图组件只做「像素 ↔ 状态」的胶水；
 * - 投影精度与边界（±85.05°、跨 180° 经线）可以被测试钉住。
 *
 * 说明：这里不处理跨 180° 经线的环绕显示，与产品当前「单城市示意图」的
 * 能力边界一致；缩放到极值时的钳制由 [MAX_LAT] 与 [MIN_ZOOM]/[MAX_ZOOM] 保证。
 */
object WebMercator {

    private const val TILE_SIZE = 256.0

    /** Mercator 投影的可视纬度上限，超出后 y 会发散。 */
    const val MAX_LAT = 85.05112878

    const val MIN_ZOOM = 3f
    const val MAX_ZOOM = 19f

    /** 某一缩放级别下整个世界地图的边长（像素）。 */
    fun worldSize(zoom: Float): Double = TILE_SIZE * 2.0.pow(zoom.toDouble())

    fun project(point: GeoPoint, zoom: Float): PlanarPoint {
        val world = worldSize(zoom)
        val lat = point.lat.coerceIn(-MAX_LAT, MAX_LAT)
        val x = (point.lng + 180.0) / 360.0 * world
        val latRad = Math.toRadians(lat)
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * world
        return PlanarPoint(x, y)
    }

    fun unproject(planar: PlanarPoint, zoom: Float): GeoPoint {
        val world = worldSize(zoom)
        val lng = planar.x / world * 360.0 - 180.0
        val n = PI - 2.0 * PI * planar.y / world
        val lat = Math.toDegrees(atan(sinh(n)))
        return GeoPoint(lat, lng)
    }

    /** 地理点 → 画布像素点。 */
    fun toScreen(
        point: GeoPoint,
        center: GeoPoint,
        zoom: Float,
        widthPx: Int,
        heightPx: Int,
    ): PlanarPoint {
        val world = project(point, zoom)
        val centerWorld = project(center, zoom)
        return PlanarPoint(
            x = world.x - centerWorld.x + widthPx / 2.0,
            y = world.y - centerWorld.y + heightPx / 2.0,
        )
    }

    /** 画布像素点 → 地理点。 */
    fun toGeo(
        screenX: Double,
        screenY: Double,
        center: GeoPoint,
        zoom: Float,
        widthPx: Int,
        heightPx: Int,
    ): GeoPoint {
        val centerWorld = project(center, zoom)
        return unproject(
            PlanarPoint(
                x = centerWorld.x + screenX - widthPx / 2.0,
                y = centerWorld.y + screenY - heightPx / 2.0,
            ),
            zoom,
        )
    }

    /** 把缩放值钳制到可用区间，避免缩放手势把地图推出可渲染范围。 */
    fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
}
