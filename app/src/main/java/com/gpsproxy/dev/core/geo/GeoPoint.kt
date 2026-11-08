package com.gpsproxy.dev.core.geo

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lng: Double) {
    fun isValid(): Boolean = lat in -90.0..90.0 && lng in -180.0..180.0

    fun formatted(): String = String.format(java.util.Locale.US, "%.6f, %.6f", lat, lng)
}

enum class CoordSystem(val label: String) {
    GCJ02("GCJ-02"),
    WGS84("WGS-84"),
    BD09("BD-09"),
}

/**
 * Public, widely documented coordinate transforms. GCJ-02 only applies inside mainland China;
 * coordinates outside China are returned unchanged.
 */
object CoordTransform {

    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val X_PI = Math.PI * 3000.0 / 180.0

    fun outOfChina(lat: Double, lng: Double): Boolean =
        lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271

    fun transform(point: GeoPoint, from: CoordSystem, to: CoordSystem): GeoPoint {
        if (from == to) return point
        val wgs = when (from) {
            CoordSystem.WGS84 -> point
            CoordSystem.GCJ02 -> gcj02ToWgs84(point)
            CoordSystem.BD09 -> gcj02ToWgs84(bd09ToGcj02(point))
        }
        return when (to) {
            CoordSystem.WGS84 -> wgs
            CoordSystem.GCJ02 -> wgs84ToGcj02(wgs)
            CoordSystem.BD09 -> gcj02ToBd09(wgs84ToGcj02(wgs))
        }
    }

    fun wgs84ToGcj02(point: GeoPoint): GeoPoint {
        if (outOfChina(point.lat, point.lng)) return point
        var dLat = transformLat(point.lng - 105.0, point.lat - 35.0)
        var dLng = transformLng(point.lng - 105.0, point.lat - 35.0)
        val radLat = point.lat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * Math.PI)
        return GeoPoint(point.lat + dLat, point.lng + dLng)
    }

    fun gcj02ToWgs84(point: GeoPoint): GeoPoint {
        if (outOfChina(point.lat, point.lng)) return point
        var lat = point.lat
        var lng = point.lng
        repeat(8) {
            val gcj = wgs84ToGcj02(GeoPoint(lat, lng))
            lat += point.lat - gcj.lat
            lng += point.lng - gcj.lng
        }
        return GeoPoint(lat, lng)
    }

    fun gcj02ToBd09(point: GeoPoint): GeoPoint {
        val z = sqrt(point.lng * point.lng + point.lat * point.lat) + 0.00002 * sin(point.lat * X_PI)
        val theta = atan2(point.lat, point.lng) + 0.000003 * cos(point.lng * X_PI)
        return GeoPoint(z * sin(theta) + 0.006, z * cos(theta) + 0.0065)
    }

    fun bd09ToGcj02(point: GeoPoint): GeoPoint {
        val x = point.lng - 0.0065
        val y = point.lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        return GeoPoint(z * sin(theta), z * cos(theta))
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Math.PI) + 320 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}
