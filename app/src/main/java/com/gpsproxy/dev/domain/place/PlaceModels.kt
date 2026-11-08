package com.gpsproxy.dev.domain.place

import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.geo.GeoPoint

data class FavoritePlace(
    val id: Long,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val coordSystem: CoordSystem = CoordSystem.GCJ02,
    val createdAt: Long,
    val lastUsedAt: Long,
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(lat, lng)
}

data class HistoryEntry(
    val id: Long,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val usedAt: Long,
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(lat, lng)
}

/**
 * 判定两条收藏/历史是否指向同一个地点。
 *
 * 收藏的去重规则是「同名或同坐标」；历史的去重规则更严格，
 * 必须名称与坐标都相同才替换。两条规则此前分别内联在两个仓库方法里，
 * 容易在后续改动中被写成三套不一致的判断，因此收敛到这里并有单测。
 */
object PlaceIdentity {

    private const val COORD_EPSILON = 1e-6

    fun sameFavorite(a: FavoritePlace, b: FavoritePlace): Boolean =
        a.name == b.name || sameCoordinate(a.lat, a.lng, b.lat, b.lng)

    fun sameHistory(a: HistoryEntry, b: HistoryEntry): Boolean =
        a.name == b.name && sameCoordinate(a.lat, a.lng, b.lat, b.lng)

    fun sameCoordinate(latA: Double, lngA: Double, latB: Double, lngB: Double): Boolean =
        kotlin.math.abs(latA - latB) < COORD_EPSILON && kotlin.math.abs(lngA - lngB) < COORD_EPSILON
}
