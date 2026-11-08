package com.gpsproxy.dev.core.geo

import kotlin.math.abs

/**
 * 解析后的坐标。
 *
 * [coordSystem] 为 null 表示"按当前设置的解释"（普通十进制输入）；
 * `geo:` 明确是 WGS-84，高德链接明确是 GCJ-02，调用方据此决定是否转换。
 */
data class ParsedCoordinate(
    val point: GeoPoint,
    val coordSystem: CoordSystem?,
)

/**
 * 从剪贴板文本中解析坐标。
 *
 * 支持：
 * - `30.2502,120.1488`、`30.2502 120.1488`、`30.2502;120.1488`
 * - `N30°15'00.8" E120°08'55.7"`、`30°15.013'N 120°08.929'E`
 * - `geo:30.2502,120.1488`（WGS-84）
 * - 高德 marker 链接中的 `position=lng,lat`（GCJ-02）
 *
 * 歧义规则：两个纯十进制数中，若首值绝对值 > 90 且次值 ≤ 90，按 `lng,lat` 处理；
 * 否则按 `lat,lng`。这是最符合用户粘贴习惯的保守推断。
 */
object CoordinateParser {

    private val NUMBER = Regex("-?\\d+(?:\\.\\d+)?")

    private val AMAP_POSITION = Regex(
        """[?&]position=(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    private val DMS = Regex(
        """(?<lead>[NSEW])?\s*(?<deg>\d{1,3}(?:\.\d+)?)\s*[°º]\s*""" +
            """(?:(?<min>\d{1,2}(?:\.\d+)?)\s*['′]\s*)?""" +
            """(?:(?<sec>\d{1,2}(?:\.\d+)?)\s*["″]\s*)?""" +
            """(?<trail>[NSEW])?""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(input: String): ParsedCoordinate? {
        val text = input.trim()
        if (text.isEmpty()) return null

        if (text.startsWith("geo:", ignoreCase = true)) {
            val numbers = NUMBER.findAll(text.substringAfter(':')).map { it.value.toDouble() }.toList()
            if (numbers.size < 2) return null
            val (lat, lng) = orient(numbers[0], numbers[1])
            return GeoPoint(lat, lng).takeIf { it.isValid() }?.let { ParsedCoordinate(it, CoordSystem.WGS84) }
        }

        AMAP_POSITION.find(text)?.let { match ->
            val lng = match.groupValues[1].toDoubleOrNull() ?: return@let
            val lat = match.groupValues[2].toDoubleOrNull() ?: return@let
            return GeoPoint(lat, lng).takeIf { it.isValid() }
                ?.let { ParsedCoordinate(it, CoordSystem.GCJ02) }
        }

        parseDms(text)?.let { return ParsedCoordinate(it, null) }

        val numbers = NUMBER.findAll(text).map { it.value.toDouble() }.toList()
        if (numbers.size < 2) return null
        val (lat, lng) = orient(numbers[0], numbers[1])
        return GeoPoint(lat, lng).takeIf { it.isValid() }?.let { ParsedCoordinate(it, null) }
    }

    private fun orient(first: Double, second: Double): Pair<Double, Double> =
        if (abs(first) > 90.0 && abs(second) <= 90.0) second to first else first to second

    private fun parseDms(text: String): GeoPoint? {
        val matches = DMS.findAll(text).filter { it.value.contains('°') || it.value.contains('º') }
            .toList()
        if (matches.size < 2) return null
        val parsed = matches.map { match ->
            val degree = match.groups["deg"]?.value?.toDoubleOrNull() ?: return null
            val minute = match.groups["min"]?.value?.toDoubleOrNull() ?: 0.0
            val second = match.groups["sec"]?.value?.toDoubleOrNull() ?: 0.0
            if (minute >= 60.0 || second >= 60.0) return null
            val hemisphere = (match.groups["lead"]?.value ?: match.groups["trail"]?.value)
                ?.uppercase()
            val value = degree + minute / 60.0 + second / 3600.0
            hemisphere to value
        }
        var latitude: Double? = null
        var longitude: Double? = null
        for ((hemisphere, value) in parsed) {
            when (hemisphere) {
                "N" -> latitude = value
                "S" -> latitude = -value
                "E" -> longitude = value
                "W" -> longitude = -value
            }
        }
        if (latitude != null && longitude != null) {
            return GeoPoint(latitude, longitude).takeIf { it.isValid() }
        }
        // 只有一个带半球：另一个按"无半球"推断轴向。
        val withoutHemisphere = parsed.filter { it.first == null }.map { it.second }
        if (latitude != null && withoutHemisphere.size == 1) {
            return GeoPoint(latitude, withoutHemisphere.single()).takeIf { it.isValid() }
        }
        if (longitude != null && withoutHemisphere.size == 1) {
            return GeoPoint(withoutHemisphere.single(), longitude).takeIf { it.isValid() }
        }
        return null
    }
}
