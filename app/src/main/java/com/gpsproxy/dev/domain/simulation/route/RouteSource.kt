package com.gpsproxy.dev.domain.simulation.route

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.domain.simulation.model.TravelMode

/**
 * 轨迹来源的展示标签。
 *
 * 界面上必须如实标注轨迹是「真实路网规划」「内置合成曲线」「导入轨迹」
 * 还是「随机漫游」：把内置曲线伪装成路网、把导入轨迹伪装成实时规划
 * 都属于误导用户。
 */
enum class RouteSourceKind {
    NETWORK,
    SYNTHETIC,
    IMPORTED,
    ROAM,
}

data class RoutePlan(
    val points: List<GeoPoint>,
    val source: RouteSourceKind,
    /** 回退到 [source] 的上游原因；正常规划时为 null。 */
    val degradeReason: String? = null,
)

/**
 * 一次轨迹规划请求。
 *
 * 用请求对象而不是继续追加 `start/end/mode/waypoints` 参数：途经点、未来
 * 可能出现的限速/避让等选项都只改这个值对象，实现方的签名保持稳定。
 */
data class RouteRequest(
    val start: GeoPoint,
    val end: GeoPoint,
    val waypoints: List<GeoPoint> = emptyList(),
    val mode: TravelMode = TravelMode.DRIVING,
) {
    /** 起点 → 途经点… → 终点的有序停靠列表。 */
    val stops: List<GeoPoint>
        get() = buildList {
            add(start)
            addAll(waypoints)
            add(end)
        }
}

/**
 * 轨迹规划端口。
 *
 * 旧实现把"两点贝塞尔"写死在控制器里，既无法接入真实路网，也无法替换。
 * 抽成端口后可以按「网络优先、内置兜底」组合，并在巡航页显示来源。
 */
interface RouteSource {

    /**
     * 规划一条轨迹。
     *
     * @return 失败返回 null（由调用方决定是否回退到内置曲线）；
     *         成功时 [RoutePlan.points] 至少包含起点与终点
     */
    suspend fun plan(request: RouteRequest): RoutePlan?
}

/**
 * 内置合成轨迹：逐段使用二次贝塞尔曲线（带垂直方向的鼓包）后拼接。
 *
 * 用于没有配置高德 Key 或网络不可用的场景，保证功能始终可用。
 * 单段算法与旧版 `GeoUtils.buildSyntheticRoute` 完全一致，避免行为漂移；
 * 多段只是在接缝处去掉重复点。
 */
class SyntheticRouteSource : RouteSource {

    override suspend fun plan(request: RouteRequest): RoutePlan? {
        val points = GeoUtils.buildSyntheticMultiRoute(request.stops)
        if (points.size < 2) return null
        return RoutePlan(points = points, source = RouteSourceKind.SYNTHETIC)
    }
}

/**
 * 链式回退：先尝试 [primary]，失败或为空时用 [fallback]。
 *
 * 用组合而不是继承：后续加入缓存、埋点、限流都只需再包一层。
 */
class FallbackRouteSource(
    private val primary: RouteSource,
    private val fallback: RouteSource,
) : RouteSource {

    override suspend fun plan(request: RouteRequest): RoutePlan? {
        val primaryResult = runCatching { primary.plan(request) }
        val planned = primaryResult.getOrNull()
        if (planned != null && planned.points.size >= 2) return planned
        // 回退必须留下原因，否则用户只看到「内置曲线」却不知道为什么退化。
        val reason = primaryResult.exceptionOrNull()
            ?.let { it.message?.takeIf(String::isNotBlank) ?: it.javaClass.simpleName }
            ?: "primary source returned no usable points"
        return fallback.plan(request)?.copy(degradeReason = reason.take(MAX_REASON_LENGTH))
    }

    private companion object {
        /** 上游异常文本可能很长，截断后再进入状态与日志。 */
        const val MAX_REASON_LENGTH = 160
    }
}
