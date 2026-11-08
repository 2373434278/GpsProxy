package com.gpsproxy.dev.data.poi

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.data.LocalPoiData
import com.gpsproxy.dev.domain.place.Poi
import com.gpsproxy.dev.domain.place.PoiCategory
import com.gpsproxy.dev.domain.place.PoiQuery
import com.gpsproxy.dev.domain.place.PoiSearchResult
import com.gpsproxy.dev.domain.place.PoiSearchSource
import com.gpsproxy.dev.domain.place.PoiSource

/**
 * 内置示例数据源。
 *
 * 无网络、无 Key 时的兜底实现：按关键词/分类/半径过滤后按距离排序分页。
 * 结果来源标注为「内置示例数据」，界面据此提示用户配置高德 Key 可获得真实数据。
 */
class LocalPoiSource(
    private val data: List<Poi> = LocalPoiData.all,
    private val pageSize: Int = PAGE_SIZE,
) : PoiSearchSource {

    override val hasLiveSource: Boolean = false

    override suspend fun search(query: PoiQuery): PoiSearchResult {
        val keyword = query.query.trim()
        var sequence = data.asSequence()
        if (keyword.isNotEmpty()) {
            sequence = sequence.filter {
                it.name.contains(keyword, ignoreCase = true) ||
                    it.address.contains(keyword, ignoreCase = true) ||
                    it.city.contains(keyword, ignoreCase = true)
            }
        }
        if (query.category != PoiCategory.ALL) {
            sequence = sequence.filter { it.category == query.category }
        }
        if (query.nearby && query.center != null) {
            val center = query.center
            sequence = sequence.filter {
                GeoUtils.distanceMeters(center, GeoPoint(it.lat, it.lng)) <= query.radiusMeters
            }
        }
        val sorted = if (query.center != null) {
            sequence.sortedBy { GeoUtils.distanceMeters(query.center, GeoPoint(it.lat, it.lng)) }
        } else {
            sequence.sortedBy { it.name }
        }.toList()
        val from = query.page * pageSize
        return PoiSearchResult(
            items = sorted.drop(from).take(pageSize),
            source = PoiSource.LOCAL,
            hasMore = sorted.size > from + pageSize,
        )
    }

    override suspend fun reverseGeocode(point: GeoPoint): String? {
        val nearest = data.minByOrNull { GeoUtils.distanceMeters(point, GeoPoint(it.lat, it.lng)) }
            ?: return null
        val distance = GeoUtils.distanceMeters(point, GeoPoint(nearest.lat, nearest.lng))
        return if (distance < NEAREST_RADIUS_METERS) {
            StringsHolder.text { place.nearAddress(nearest.city, nearest.name) }
        } else {
            null
        }
    }

    companion object {
        const val PAGE_SIZE = 20
        private const val NEAREST_RADIUS_METERS = 3000.0
    }
}
