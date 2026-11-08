package com.gpsproxy.dev.domain.place

import com.gpsproxy.dev.core.geo.GeoPoint
import kotlinx.coroutines.flow.Flow

/**
 * 收藏 / 历史 / 搜索词的存储端口。
 *
 * 领域与表现层只依赖这些语义操作，不关心底层是 DataStore + JSON 还是数据库。
 * 所有写操作都应是幂等的（重复收藏同一地点不会产生重复条目）。
 */
interface PlacesStore {

    val favorites: Flow<List<FavoritePlace>>
    val history: Flow<List<HistoryEntry>>
    val searchHistory: Flow<List<String>>

    suspend fun addFavorite(name: String, address: String, point: GeoPoint, coord: com.gpsproxy.dev.core.geo.CoordSystem)
    suspend fun renameFavorite(id: Long, newName: String)
    suspend fun removeFavorite(id: Long)
    suspend fun restoreFavorite(place: FavoritePlace)
    suspend fun touchFavorite(id: Long)
    suspend fun addHistory(name: String, address: String, point: GeoPoint)
    suspend fun clearHistory()
    suspend fun addSearchTerm(term: String)
    suspend fun clearSearchTerms()
}
