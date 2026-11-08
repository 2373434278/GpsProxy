package com.gpsproxy.dev.domain.place

import com.gpsproxy.dev.core.geo.GeoPoint

enum class PoiCategory(val amapType: String) {
    ALL(""),
    SCENIC("110000"),
    FOOD("050000"),
    TRANSIT("150000"),
    MALL("060100"),
    HOTEL("100000"),
    HOSPITAL("090100"),
    OTHER(""),
}

enum class PoiSource {
    LOCAL,
    AMAP,
}

data class Poi(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val category: PoiCategory,
    val city: String,
)

data class PoiSearchResult(
    val items: List<Poi>,
    val source: PoiSource,
    val warning: String? = null,
    val hasMore: Boolean = false,
)

/**
 * 一次 POI 查询的完整参数。
 *
 * 用值对象而不是 6 个位置参数：搜索页的「关键词 / 周边 / 历史」三种模式
 * 共享同一套字段，值对象同时也是缓存键的依据。
 */
data class PoiQuery(
    val query: String,
    val category: PoiCategory,
    val center: GeoPoint?,
    val nearby: Boolean,
    val radiusMeters: Int,
    val page: Int,
)

/**
 * POI 数据源端口（实现可以是本地、远程或二者组合）。
 *
 * 实现可以远程、可以本地、可以是"远程失败回落本地"的组合；
 * 调用方只关心结果与来源标注（UI 必须如实显示数据来自高德还是内置示例）。
 */
interface PoiSearchSource {

    /** 是否具备实时数据能力（用于设置页展示，不影响调用）。 */
    val hasLiveSource: Boolean

    suspend fun search(query: PoiQuery): PoiSearchResult

    /** 逆地理编码；无法解析时返回 null，由调用方回落到坐标展示。 */
    suspend fun reverseGeocode(point: GeoPoint): String?
}
