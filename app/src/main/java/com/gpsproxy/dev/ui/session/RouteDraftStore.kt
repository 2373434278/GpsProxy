package com.gpsproxy.dev.ui.session

import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.StringsHolder

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.domain.simulation.model.TravelMode
import com.gpsproxy.dev.domain.simulation.route.RoamPlanner
import com.gpsproxy.dev.domain.simulation.route.SavedRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RouteWaypoint(
    val point: GeoPoint,
    val address: String,
)

data class RouteDraft(
    val start: GeoPoint? = null,
    val startAddress: String = "",
    val end: GeoPoint? = null,
    val endAddress: String = "",
    val waypoints: List<RouteWaypoint> = emptyList(),
    val kind: RouteKind = RouteKind.POINT_TO_POINT,
    val trackPoints: List<GeoPoint> = emptyList(),
    val trackName: String = "",
    val roamRadiusMeters: Int = 500,
    val mode: TravelMode = TravelMode.DRIVING,
    val speedKmh: Int = 32,
    val loop: Boolean = false,
    val roundTrip: Boolean = false,
) {
    val isComplete: Boolean
        get() = when (kind) {
            RouteKind.POINT_TO_POINT -> start != null && end != null
            RouteKind.ROAM -> start != null
            RouteKind.IMPORTED -> trackPoints.size >= 2
        }

    /** 预览用折线：两点路线按合成曲线逐段连接；漫游画一个半径圆；导入直接返回轨迹。 */
    fun previewPoints(): List<GeoPoint> = when (kind) {
        RouteKind.IMPORTED -> trackPoints
        RouteKind.ROAM -> {
            val center = start ?: return emptyList()
            GeoUtils.circlePath(center, roamRadiusMeters.toDouble(), segments = 64)
        }
        RouteKind.POINT_TO_POINT -> {
            val from = start ?: return emptyList()
            val to = end ?: return emptyList()
            val stops = buildList {
                add(from)
                waypoints.forEach { add(it.point) }
                add(to)
            }
            GeoUtils.buildSyntheticMultiRoute(stops)
        }
    }

    fun toConfig(): RouteConfig? = when (kind) {
        RouteKind.POINT_TO_POINT -> {
            val from = start ?: return null
            val to = end ?: return null
            RouteConfig(
                start = from,
                end = to,
                mode = mode,
                speedKmh = speedKmh,
                loop = loop,
                roundTrip = roundTrip,
                waypoints = waypoints.map { it.point },
                kind = RouteKind.POINT_TO_POINT,
            )
        }

        RouteKind.ROAM -> {
            val anchor = start ?: return null
            RouteConfig(
                start = anchor,
                end = anchor,
                mode = TravelMode.WALKING,
                speedKmh = speedKmh,
                loop = true,
                roundTrip = false,
                kind = RouteKind.ROAM,
                roamRadiusMeters = roamRadiusMeters,
            )
        }

        RouteKind.IMPORTED -> {
            val points = trackPoints
            if (points.size < 2) return null
            RouteConfig(
                start = points.first(),
                end = points.last(),
                mode = mode,
                speedKmh = speedKmh,
                loop = loop,
                roundTrip = roundTrip,
                kind = RouteKind.IMPORTED,
                trackPoints = points,
            )
        }
    }
}

/**
 * 巡航草稿的跨屏状态。
 *
 * 选终点/途经点需要跳到地图页，再回到配置页继续编辑；草稿必须活在两个页面之外。
 * 规则（循环与往返互斥、模式切换重置推荐速度、途经点去重与上限）也集中在这里，
 * 避免界面层各写一遍。
 */
class RouteDraftStore {

    private val _draft = MutableStateFlow(RouteDraft())
    val draft: StateFlow<RouteDraft> = _draft.asStateFlow()

    /** 选点页回来后要写入的途经点下标；-1 表示追加。 */
    private var pendingWaypointIndex: Int? = null

    /** 打开巡航页时，把当前模拟位置作为默认起点（不覆盖用户已设置的起点）。 */
    fun initializeStart(point: GeoPoint, address: String) {
        _draft.update { current ->
            if (current.start != null) current else current.copy(start = point, startAddress = address)
        }
    }

    fun setStart(point: GeoPoint, address: String) {
        _draft.update { it.copy(start = point, startAddress = address) }
    }

    fun setEnd(point: GeoPoint, address: String) {
        _draft.update { it.copy(end = point, endAddress = address) }
    }

    fun setKind(kind: RouteKind) {
        _draft.update { current ->
            when (kind) {
                RouteKind.ROAM -> current.copy(
                    kind = kind,
                    waypoints = emptyList(),
                    end = null,
                    endAddress = "",
                    loop = true,
                    roundTrip = false,
                    speedKmh = 5,
                )
                RouteKind.IMPORTED -> current.copy(
                    kind = kind,
                    waypoints = emptyList(),
                    loop = false,
                    roundTrip = false,
                )
                RouteKind.POINT_TO_POINT -> current.copy(
                    kind = kind,
                    trackPoints = emptyList(),
                    trackName = "",
                    loop = false,
                    roundTrip = false,
                )
            }
        }
    }

    fun setMode(mode: TravelMode) {
        _draft.update { it.copy(mode = mode, speedKmh = mode.defaultSpeedKmh) }
    }

    fun setSpeed(speedKmh: Int) {
        _draft.update {
            val min = if (it.kind == RouteKind.ROAM) 1 else 5
            it.copy(speedKmh = speedKmh.coerceIn(min, 80))
        }
    }

    /** 循环与往返互斥：开启一个会自动关闭另一个。 */
    fun setLoop(enabled: Boolean) {
        _draft.update { it.copy(loop = enabled, roundTrip = if (enabled) false else it.roundTrip) }
    }

    fun setRoundTrip(enabled: Boolean) {
        _draft.update { it.copy(roundTrip = enabled, loop = if (enabled) false else it.loop) }
    }

    fun setRoamRadius(meters: Int) {
        _draft.update {
            it.copy(
                roamRadiusMeters = meters.coerceIn(
                    RoamPlanner.MIN_RADIUS_METERS,
                    RoamPlanner.MAX_RADIUS_METERS,
                ),
            )
        }
    }

    fun setImportedTrack(points: List<GeoPoint>, name: String) {
        if (points.size < 2) return
        _draft.update {
            it.copy(
                kind = RouteKind.IMPORTED,
                trackPoints = points,
                trackName = name,
                start = points.first(),
                end = points.last(),
                waypoints = emptyList(),
                loop = false,
                roundTrip = false,
            )
        }
    }

    fun prepareWaypointPick(index: Int) {
        pendingWaypointIndex = index
    }

    fun consumePendingWaypointIndex(): Int = pendingWaypointIndex ?: ADD_WAYPOINT

    fun addWaypoint(point: GeoPoint, address: String) {
        _draft.update { current ->
            if (current.waypoints.size >= MAX_WAYPOINTS) return@update current
            if (current.waypoints.any { it.point == point } || point == current.start || point == current.end) {
                return@update current
            }
            current.copy(waypoints = current.waypoints + RouteWaypoint(point, address))
        }
    }

    fun setWaypoint(index: Int, point: GeoPoint, address: String) {
        _draft.update { current ->
            if (index !in current.waypoints.indices) return@update current
            current.copy(
                waypoints = current.waypoints.mapIndexed { at, existing ->
                    if (at == index) RouteWaypoint(point, address) else existing
                },
            )
        }
    }

    fun removeWaypoint(index: Int) {
        _draft.update { current ->
            if (index !in current.waypoints.indices) return@update current
            current.copy(waypoints = current.waypoints.filterIndexed { at, _ -> at != index })
        }
    }

    /** 上移/下移；越界时静默不动。 */
    fun moveWaypoint(index: Int, delta: Int) {
        _draft.update { current ->
            val target = index + delta
            if (index !in current.waypoints.indices || target !in current.waypoints.indices) return@update current
            val list = current.waypoints.toMutableList()
            val item = list.removeAt(index)
            list.add(target, item)
            current.copy(waypoints = list)
        }
    }

    /** 从路线库载入：只填草稿，不自动开始。 */
    fun loadSavedRoute(saved: SavedRoute, strings: Strings = StringsHolder.current) {
        val config = saved.config
        val route = strings.route
        _draft.value = RouteDraft(
            start = config.start,
            startAddress = route.libraryStart,
            end = config.end,
            endAddress = route.libraryEnd,
            waypoints = config.waypoints.map { RouteWaypoint(it, route.waypointLabel) },
            kind = config.kind,
            trackPoints = config.trackPoints,
            trackName = saved.name,
            roamRadiusMeters = config.roamRadiusMeters,
            mode = config.mode,
            speedKmh = config.speedKmh,
            loop = config.loop,
            roundTrip = config.roundTrip,
        )
    }

    companion object {
        const val MAX_WAYPOINTS = 8
        const val ADD_WAYPOINT = -1
    }
}
