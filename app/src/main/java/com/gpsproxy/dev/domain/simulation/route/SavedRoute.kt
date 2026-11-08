package com.gpsproxy.dev.domain.simulation.route

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import kotlinx.coroutines.flow.Flow

/**
 * 一条可复用的轨迹（收藏或最近使用）。
 *
 * 只保存"如何重建轨迹"的规格，不保存运行进度：路线库的语义是
 * "下次还走这条路"，而不是"从上次中断处继续"。
 */
data class SavedRoute(
    val id: Long,
    val name: String,
    val config: RouteConfig,
    val favorite: Boolean,
    val createdAt: Long,
    val lastUsedAt: Long,
)

/**
 * 路线收藏 / 最近路线存储端口。
 *
 * 与 [com.gpsproxy.dev.domain.place.PlacesStore] 分开：点收藏与轨迹收藏的
 * 去重键、容量上限、清理规则都不同，混在一张表里会让两边互相裁剪。
 */
interface RouteStore {

    val routes: Flow<List<SavedRoute>>

    suspend fun recordRecent(config: RouteConfig, name: String)

    /** 记录并立即标记为收藏，返回条目 id。 */
    suspend fun saveFavorite(config: RouteConfig, name: String): Long

    suspend fun setFavorite(id: Long, favorite: Boolean)

    suspend fun rename(id: Long, name: String)

    suspend fun remove(id: Long)

    suspend fun touch(id: Long)
}

/**
 * 轨迹签名：判断"是不是同一条路线"。
 *
 * 有意不包含速度、循环、往返：用户把同一条路线从 32km/h 调到 50km/h
 * 不应该在最近列表里多出一条。漫游半径与导入轨迹内容参与签名。
 */
object RouteSignature {

    fun of(config: RouteConfig): String = buildString {
        append(config.kind.name).append('|')
        append(config.start.formatted()).append('|')
        append(config.end.formatted()).append('|')
        if (config.kind == RouteKind.ROAM) {
            append("r=").append(config.roamRadiusMeters).append('|')
        }
        config.waypoints.forEach { append(it.formatted()).append(';') }
        if (config.kind == RouteKind.IMPORTED) {
            append("|t=").append(config.trackPoints.size)
                .append('|').append(config.trackPoints.hashCode())
        }
    }
}

/**
 * 路线库的纯策略：记录、去重、容量裁剪。
 *
 * 放在领域层而不是仓库里，是为了让"同一条路线不重复、最近 10 条、
 * 收藏最多 50 条、收藏不被最近列表挤掉"这些规则可以被毫秒级单测直接断言。
 */
object RouteHistoryPolicy {

    const val MAX_RECENT = 10
    const val MAX_FAVORITES = 50

    /** 路线库只保存形状：导入轨迹降采样，避免 DataStore 被上万点撑爆。 */
    const val MAX_STORED_TRACK_POINTS = 2_000

    fun defaultName(
        config: RouteConfig,
        strings: Strings = StringsHolder.current,
    ): String {
        val route = strings.route
        return when (config.kind) {
            RouteKind.ROAM -> route.savedNameRoam(config.roamRadiusMeters)
            RouteKind.IMPORTED -> route.savedNameImported(config.trackPoints.size)
            RouteKind.POINT_TO_POINT ->
                route.savedNameEndpoints(config.start.formatted(), config.end.formatted())
        }
    }

    fun sanitize(config: RouteConfig): RouteConfig = when (config.kind) {
        RouteKind.IMPORTED -> config.copy(
            trackPoints = GeoUtils.downsample(config.trackPoints, MAX_STORED_TRACK_POINTS),
        )
        else -> config
    }

    /** 记录一次使用：命中签名则刷新时间与内容，否则插入一条最近路线。 */
    fun recordRecent(
        existing: List<SavedRoute>,
        config: RouteConfig,
        name: String,
        now: Long,
    ): List<SavedRoute> {
        val sanitized = sanitize(config)
        val signature = RouteSignature.of(sanitized)
        val matched = existing.firstOrNull { RouteSignature.of(it.config) == signature }
        val updated = if (matched != null) {
            existing.map {
                if (it.id == matched.id) {
                    it.copy(
                        // 再次使用不覆盖用户改过的名字；只补空名称。
                        name = it.name.ifBlank { name },
                        config = sanitized,
                        lastUsedAt = now,
                    )
                } else {
                    it
                }
            }
        } else {
            existing + SavedRoute(
                id = now,
                name = name.ifBlank { defaultName(sanitized) },
                config = sanitized,
                favorite = false,
                createdAt = now,
                lastUsedAt = now,
            )
        }
        return trim(updated)
    }

    fun setFavorite(existing: List<SavedRoute>, id: Long, favorite: Boolean): List<SavedRoute> =
        trim(existing.map { if (it.id == id) it.copy(favorite = favorite) else it })

    fun rename(existing: List<SavedRoute>, id: Long, name: String): List<SavedRoute> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return existing
        return existing.map { if (it.id == id) it.copy(name = trimmed.take(40)) else it }
    }

    fun remove(existing: List<SavedRoute>, id: Long): List<SavedRoute> =
        existing.filterNot { it.id == id }

    fun touch(existing: List<SavedRoute>, id: Long, now: Long): List<SavedRoute> =
        existing.map { if (it.id == id) it.copy(lastUsedAt = now) else it }

    /**
     * 裁剪：收藏优先保留（最多 50 条，超出淘汰最久未用），
     * 非收藏只保留最近 10 条。收藏永远不会因为没有再使用而被最近列表挤掉。
     */
    private fun trim(existing: List<SavedRoute>): List<SavedRoute> {
        val favorites = existing.filter { it.favorite }
            .sortedByDescending { it.lastUsedAt }
            .take(MAX_FAVORITES)
        val recents = existing.filterNot { it.favorite }
            .sortedByDescending { it.lastUsedAt }
            .take(MAX_RECENT)
        return (favorites + recents).sortedByDescending { it.lastUsedAt }
    }
}

/** 便于测试与 UI 预览：把点列表转成可读的首尾描述。 */
fun RouteConfig.endpointSummary(strings: Strings = StringsHolder.current): String =
    strings.route.endpointSummary(start.formatted(), end.formatted(), waypoints.size)

/** 判断配置是否足以启动（不依赖 Android）。 */
fun RouteConfig.isStartable(): Boolean = when (kind) {
    RouteKind.ROAM -> roamRadiusMeters >= RoamPlanner.MIN_RADIUS_METERS
    RouteKind.IMPORTED -> trackPoints.size >= 2
    RouteKind.POINT_TO_POINT -> start != end || waypoints.isNotEmpty()
}

/** 首尾点（导入轨迹以实际折线为准）。 */
fun RouteConfig.displayStart(): GeoPoint = if (kind == RouteKind.IMPORTED) {
    trackPoints.firstOrNull() ?: start
} else {
    start
}

fun RouteConfig.displayEnd(): GeoPoint = if (kind == RouteKind.IMPORTED) {
    trackPoints.lastOrNull() ?: end
} else {
    end
}
