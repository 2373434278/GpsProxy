package com.gpsproxy.dev.domain.place.usecase

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.result.Outcome
import com.gpsproxy.dev.core.result.failure
import com.gpsproxy.dev.core.result.success
import com.gpsproxy.dev.domain.place.FavoritePlace
import com.gpsproxy.dev.domain.place.HistoryEntry
import com.gpsproxy.dev.domain.place.PlacesStore
import com.gpsproxy.dev.domain.place.PoiQuery
import com.gpsproxy.dev.domain.place.PoiSearchResult
import com.gpsproxy.dev.domain.place.PoiSearchSource
import com.gpsproxy.dev.domain.simulation.port.SimulationControl

/**
 * 选点与历史/收藏的应用服务。
 *
 * 把"校验坐标 → 写入引擎 → 记录历史"的顺序固化下来，避免 UI 各处
 * 自己拼装导致有的路径写历史、有的不写（旧实现就存在这种差异）。
 */
class PlaceUseCases(
    private val engine: SimulationControl,
    private val places: PlacesStore,
) {

    /**
     * 应用一个选点。
     *
     * @param recordHistory 是否写入历史（选点页确认时为 true，巡航终点选择为 false）
     */
    suspend fun applyPoint(
        point: GeoPoint,
        address: String,
        historyName: String = address,
        recordHistory: Boolean = true,
    ): Outcome<Unit> {
        if (!point.isValid()) {
            return failure(ErrorCode.COORD_0101, "坐标超出合法范围")
        }
        engine.setPoint(point, address)
        if (recordHistory) {
            places.addHistory(name = historyName, address = address, point = point)
        }
        return success()
    }

    suspend fun applyFavorite(favorite: FavoritePlace) {
        engine.setPoint(favorite.toGeoPoint(), favorite.name)
        places.touchFavorite(favorite.id)
        places.addHistory(favorite.name, favorite.address, favorite.toGeoPoint())
    }

    suspend fun applyHistory(entry: HistoryEntry) {
        engine.setPoint(entry.toGeoPoint(), entry.name)
    }

    suspend fun saveFavorite(name: String, address: String, point: GeoPoint, coord: com.gpsproxy.dev.core.geo.CoordSystem) {
        places.addFavorite(name = name, address = address, point = point, coord = coord)
    }

    /**
     * 搜索并记录搜索词。
     *
     * 记录条件与旧实现保持一致：只在第一页且关键词非空时写入，
     * 翻页与周边搜索不污染搜索历史。
     */
    class Search(
        private val source: PoiSearchSource,
        private val places: PlacesStore,
    ) {
        suspend fun search(query: PoiQuery, recordTerm: Boolean): PoiSearchResult {
            val result = source.search(query)
            if (recordTerm && query.page == 0 && query.query.isNotBlank()) {
                places.addSearchTerm(query.query)
            }
            return result
        }

        suspend fun reverseGeocode(point: GeoPoint): String? = source.reverseGeocode(point)
    }
}
