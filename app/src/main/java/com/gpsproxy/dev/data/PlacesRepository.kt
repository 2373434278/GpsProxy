package com.gpsproxy.dev.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.data.place.FavoriteCodec
import com.gpsproxy.dev.data.place.HistoryCodec
import com.gpsproxy.dev.data.place.SearchTermsCodec
import com.gpsproxy.dev.domain.place.FavoritePlace
import com.gpsproxy.dev.domain.place.HistoryEntry
import com.gpsproxy.dev.domain.place.PlaceIdentity
import com.gpsproxy.dev.domain.place.PlacesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.placesDataStore by preferencesDataStore(name = "gpsproxy_places")

/**
 * 收藏 / 历史 / 搜索词的持久化实现。
 *
 * 只承担「读流 + 事务内读改写」，编码与判等分别委托给 Codec 与
 * [PlaceIdentity]，因此这里不存在重复的字段列表与比较逻辑。
 */
class PlacesRepository(private val context: Context) : PlacesStore {

    private val store = context.placesDataStore

    override val favorites: Flow<List<FavoritePlace>> =
        store.data.map { FavoriteCodec.decode(it[KEY_FAVORITES]) }

    override val history: Flow<List<HistoryEntry>> =
        store.data.map { HistoryCodec.decode(it[KEY_HISTORY]) }

    override val searchHistory: Flow<List<String>> =
        store.data.map { SearchTermsCodec.decode(it[KEY_SEARCHES]) }

    override suspend fun addFavorite(
        name: String,
        address: String,
        point: GeoPoint,
        coord: CoordSystem,
    ) {
        val now = System.currentTimeMillis()
        val place = FavoritePlace(
            id = now,
            name = name,
            address = address,
            lat = point.lat,
            lng = point.lng,
            coordSystem = coord,
            createdAt = now,
            lastUsedAt = now,
        )
        store.edit { preferences ->
            val list = FavoriteCodec.decode(preferences[KEY_FAVORITES]).toMutableList()
            val existingIndex = list.indexOfFirst { PlaceIdentity.sameFavorite(it, place) }
            if (existingIndex >= 0) {
                // 同名或同坐标视为同一地点：保留原 id，刷新内容与使用时间。
                list[existingIndex] = place.copy(id = list[existingIndex].id)
            } else {
                list.add(0, place)
            }
            val trimmed = list.sortedByDescending { it.lastUsedAt }.take(FavoriteCodec.MAX_ENTRIES)
            preferences[KEY_FAVORITES] = FavoriteCodec.encode(trimmed)
        }
    }

    override suspend fun renameFavorite(id: Long, newName: String) {
        val name = newName.trim().take(MAX_NAME_LENGTH)
        if (name.isEmpty()) return
        store.edit { preferences ->
            val list = FavoriteCodec.decode(preferences[KEY_FAVORITES]).map {
                if (it.id == id) it.copy(name = name) else it
            }
            preferences[KEY_FAVORITES] = FavoriteCodec.encode(list)
        }
    }

    override suspend fun removeFavorite(id: Long) {
        store.edit { preferences ->
            val list = FavoriteCodec.decode(preferences[KEY_FAVORITES]).filterNot { it.id == id }
            preferences[KEY_FAVORITES] = FavoriteCodec.encode(list)
        }
    }

    override suspend fun restoreFavorite(place: FavoritePlace) {
        store.edit { preferences ->
            val list = FavoriteCodec.decode(preferences[KEY_FAVORITES]).toMutableList()
            if (list.none { it.id == place.id }) list.add(0, place)
            preferences[KEY_FAVORITES] = FavoriteCodec.encode(
                list.sortedByDescending { it.lastUsedAt }.take(FavoriteCodec.MAX_ENTRIES),
            )
        }
    }

    override suspend fun touchFavorite(id: Long) {
        store.edit { preferences ->
            val list = FavoriteCodec.decode(preferences[KEY_FAVORITES]).map {
                if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it
            }
            preferences[KEY_FAVORITES] = FavoriteCodec.encode(list.sortedByDescending { it.lastUsedAt })
        }
    }

    override suspend fun addHistory(name: String, address: String, point: GeoPoint) {
        val now = System.currentTimeMillis()
        val entry = HistoryEntry(now, name, address, point.lat, point.lng, now)
        store.edit { preferences ->
            val list = HistoryCodec.decode(preferences[KEY_HISTORY]).toMutableList()
            list.removeAll { PlaceIdentity.sameHistory(it, entry) }
            list.add(0, entry)
            preferences[KEY_HISTORY] = HistoryCodec.encode(list.take(HistoryCodec.MAX_ENTRIES))
        }
    }

    override suspend fun clearHistory() {
        store.edit { it.remove(KEY_HISTORY) }
    }

    override suspend fun addSearchTerm(term: String) {
        val value = term.trim()
        if (value.isEmpty()) return
        store.edit { preferences ->
            val list = SearchTermsCodec.decode(preferences[KEY_SEARCHES]).toMutableList()
            list.remove(value)
            list.add(0, value)
            preferences[KEY_SEARCHES] = SearchTermsCodec.encode(list.take(SearchTermsCodec.MAX_ENTRIES))
        }
    }

    override suspend fun clearSearchTerms() {
        store.edit { it.remove(KEY_SEARCHES) }
    }

    private companion object {
        val KEY_FAVORITES = stringPreferencesKey("favorites_json")
        val KEY_HISTORY = stringPreferencesKey("history_json")
        val KEY_SEARCHES = stringPreferencesKey("search_terms_json")

        const val MAX_NAME_LENGTH = 20
    }
}
