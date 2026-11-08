package com.gpsproxy.dev.data.route

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.domain.simulation.model.TravelMode
import com.gpsproxy.dev.domain.simulation.route.RoamPlanner
import com.gpsproxy.dev.domain.simulation.route.RouteHistoryPolicy
import com.gpsproxy.dev.domain.simulation.route.SavedRoute
import org.json.JSONArray
import org.json.JSONObject

/**
 * 路线库的 JSON 编解码。
 *
 * 与 `FavoriteCodec` 同风格：数组包对象，点用 `[lat,lng]` 紧凑表示。
 * 解码对缺字段/枚举脏值统一回落，保证旧版本或手工改坏的数据不会让路线库整体不可用。
 */
object SavedRouteCodec {

    /** 路线库总条目的硬上限（策略层已按收藏/最近分别裁剪，这里再兜底）。 */
    const val MAX_ENTRIES = 80

    fun encode(routes: List<SavedRoute>): String {
        val array = JSONArray()
        routes.take(MAX_ENTRIES).forEach { route ->
            array.put(
                JSONObject().apply {
                    put("id", route.id)
                    put("name", route.name)
                    put("favorite", route.favorite)
                    put("createdAt", route.createdAt)
                    put("lastUsedAt", route.lastUsedAt)
                    put("config", encodeConfig(route.config))
                },
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<SavedRoute> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val config = obj.optJSONObject("config")?.let(::decodeConfig) ?: continue
                    add(
                        SavedRoute(
                            id = obj.optLong("id", 0L),
                            name = obj.optString("name").take(40).ifBlank {
                                RouteHistoryPolicy.defaultName(config)
                            },
                            config = config,
                            favorite = obj.optBoolean("favorite", false),
                            createdAt = obj.optLong("createdAt", 0L),
                            lastUsedAt = obj.optLong("lastUsedAt", 0L),
                        ),
                    )
                }
            }.take(MAX_ENTRIES)
        }.getOrElse { emptyList() }
    }

    private fun encodeConfig(config: RouteConfig): JSONObject = JSONObject().apply {
        put("kind", config.kind.name)
        put("start", encodePoint(config.start))
        put("end", encodePoint(config.end))
        put("mode", config.mode.name)
        put("speedKmh", config.speedKmh)
        put("loop", config.loop)
        put("roundTrip", config.roundTrip)
        put("roamRadius", config.roamRadiusMeters)
        put("waypoints", encodePoints(config.waypoints))
        put("trackPoints", encodePoints(config.trackPoints))
    }

    private fun decodeConfig(obj: JSONObject): RouteConfig? {
        val start = decodePoint(obj.optJSONArray("start")) ?: return null
        val end = decodePoint(obj.optJSONArray("end")) ?: start
        val kind = obj.optString("kind").toEnum(RouteKind.entries, RouteKind.POINT_TO_POINT)
        val mode = obj.optString("mode").toEnum(TravelMode.entries, TravelMode.DRIVING)
        return RouteConfig(
            start = start,
            end = end,
            mode = mode,
            speedKmh = obj.optInt("speedKmh", mode.defaultSpeedKmh).coerceIn(1, 200),
            loop = obj.optBoolean("loop", false),
            roundTrip = obj.optBoolean("roundTrip", false),
            waypoints = decodePoints(obj.optJSONArray("waypoints")).take(8),
            kind = kind,
            trackPoints = GeoUtils.downsample(
                decodePoints(obj.optJSONArray("trackPoints")),
                RouteHistoryPolicy.MAX_STORED_TRACK_POINTS,
            ),
            roamRadiusMeters = obj.optInt("roamRadius", 500)
                .coerceIn(RoamPlanner.MIN_RADIUS_METERS, RoamPlanner.MAX_RADIUS_METERS),
        )
    }

    private fun encodePoints(points: List<GeoPoint>): JSONArray = JSONArray().apply {
        points.forEach { put(encodePoint(it)) }
    }

    private fun encodePoint(point: GeoPoint): JSONArray = JSONArray().apply {
        put(point.lat)
        put(point.lng)
    }

    private fun decodePoints(array: JSONArray?): List<GeoPoint> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                decodePoint(array.optJSONArray(index))?.takeIf { it.isValid() }?.let(::add)
            }
        }
    }

    private fun decodePoint(array: JSONArray?): GeoPoint? {
        if (array == null || array.length() < 2) return null
        val lat = array.optDouble(0, Double.NaN)
        val lng = array.optDouble(1, Double.NaN)
        if (lat.isNaN() || lng.isNaN() || !lat.isFinite() || !lng.isFinite()) return null
        return GeoPoint(lat, lng)
    }

    private inline fun <reified T : Enum<T>> String.toEnum(entries: List<T>, fallback: T): T =
        entries.firstOrNull { it.name == this } ?: fallback
}
