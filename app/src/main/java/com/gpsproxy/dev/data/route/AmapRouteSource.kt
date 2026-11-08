package com.gpsproxy.dev.data.route

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.data.net.HttpTextClient
import com.gpsproxy.dev.domain.simulation.model.TravelMode
import com.gpsproxy.dev.domain.simulation.route.RoutePlan
import com.gpsproxy.dev.domain.simulation.route.RouteRequest
import com.gpsproxy.dev.domain.simulation.route.RouteSource
import com.gpsproxy.dev.domain.simulation.route.RouteSourceKind
import org.json.JSONObject
import java.io.IOException

/**
 * 高德路径规划数据源。
 *
 * 与内置贝塞尔曲线的区别：轨迹会贴合真实道路，适合演示"沿某条路行驶"。
 * 但真实路网依赖网络与 Key，因此必须由 `FallbackRouteSource` 包裹：
 * 无 Key、无网络、配额用尽时自动回退内置曲线，并在界面上如实标注来源。
 *
 * 途经点策略：
 * - 驾车：高德 v3 driving 原生支持 `waypoints`（`lng,lat;lng,lat`），一次请求；
 * - 骑行/步行：其接口不支持途经点，逐段请求后拼接去重；
 * - 任一段失败返回 null，由 `FallbackRouteSource` 整体回退，避免出现"半条真路网"。
 *
 * 只解析 `extensions=base` 的折线，不依赖路况/收费等扩展字段，
 * 保证响应结构稳定、流量最小。
 */
class AmapRouteSource(
    private val client: HttpTextClient,
    private val key: String,
) : RouteSource {

    override suspend fun plan(request: RouteRequest): RoutePlan? {
        if (key.isBlank()) return null
        val points = when {
            request.waypoints.isEmpty() -> requestLeg(request.mode, request.start, request.end, emptyList())
            request.mode == TravelMode.DRIVING -> requestLeg(
                mode = request.mode,
                start = request.start,
                end = request.end,
                waypoints = request.waypoints,
            )
            else -> requestLegsSequentially(request)
        }
        if (points.size < 2) return null
        return RoutePlan(points = points, source = RouteSourceKind.NETWORK)
    }

    /** 逐段请求（骑行/步行），只在接缝处去重，不改变各段内部形状。 */
    private suspend fun requestLegsSequentially(request: RouteRequest): List<GeoPoint> {
        val stops = request.stops
        val merged = ArrayList<GeoPoint>()
        for (index in 0 until stops.size - 1) {
            val leg = requestLeg(request.mode, stops[index], stops[index + 1], emptyList())
            if (merged.isEmpty()) merged.addAll(leg) else merged.addAll(leg.drop(1))
        }
        return merged.distinct()
    }

    private suspend fun requestLeg(
        mode: TravelMode,
        start: GeoPoint,
        end: GeoPoint,
        waypoints: List<GeoPoint>,
    ): List<GeoPoint> {
        val endpoint = when (mode) {
            TravelMode.DRIVING -> "https://restapi.amap.com/v3/direction/driving"
            TravelMode.CYCLING -> "https://restapi.amap.com/v4/direction/bicycling"
            TravelMode.WALKING -> "https://restapi.amap.com/v3/direction/walking"
        }
        val params = linkedMapOf(
            "key" to key,
            "origin" to "${start.lng},${start.lat}",
            "destination" to "${end.lng},${end.lat}",
            "extensions" to "base",
        )
        if (waypoints.isNotEmpty()) {
            params["waypoints"] = waypoints.take(MAX_WAYPOINTS)
                .joinToString(";") { "${it.lng},${it.lat}" }
        }
        val json = client.get(endpoint, params)
        return parsePoints(JSONObject(json))
    }

    private fun parsePoints(root: JSONObject): List<GeoPoint> {
        // 两种响应信封：v3（驾车/步行）用 status/route，v4（骑行）用 errcode/data。
        // v4 成功响应没有 status 字段，只看 status 会把每条骑行路线都判为失败。
        if (root.has("errcode")) {
            if (root.optInt("errcode", -1) != 0) {
                val reason = root.optString("errdetail")
                    .ifBlank { root.optString("errmsg") }
                    .ifBlank { "amap route error" }
                throw IOException(reason)
            }
        } else if (root.optString("status") != "1") {
            throw IOException(root.optString("info", "amap route error"))
        }
        // driving/walking: route.paths[0].steps；bicycling(v4): data.paths[0].steps
        val route = root.optJSONObject("route") ?: root.optJSONObject("data")
        val paths = route?.optJSONArray("paths") ?: return emptyList()
        val path = paths.optJSONObject(0) ?: return emptyList()
        val steps = path.optJSONArray("steps") ?: return emptyList()
        return buildList {
            for (index in 0 until steps.length()) {
                val step = steps.optJSONObject(index) ?: continue
                val polyline = step.optString("polyline")
                if (polyline.isBlank()) continue
                polyline.split(';').forEach { pair ->
                    val parts = pair.split(',')
                    if (parts.size != 2) return@forEach
                    val lng = parts[0].toDoubleOrNull() ?: return@forEach
                    val lat = parts[1].toDoubleOrNull() ?: return@forEach
                    add(GeoPoint(lat, lng))
                }
            }
        }.distinct()
    }

    private companion object {
        /** 与产品上限一致：UI 允许 8 个途经点，高德上限 16。 */
        const val MAX_WAYPOINTS = 8
    }
}
