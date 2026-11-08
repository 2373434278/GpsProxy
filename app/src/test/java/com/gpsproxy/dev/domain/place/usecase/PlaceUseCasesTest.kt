package com.gpsproxy.dev.domain.place.usecase

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.result.Outcome
import com.gpsproxy.dev.domain.place.FavoritePlace
import com.gpsproxy.dev.domain.place.HistoryEntry
import com.gpsproxy.dev.domain.place.PlacesStore
import com.gpsproxy.dev.domain.place.PoiCategory
import com.gpsproxy.dev.domain.place.PoiQuery
import com.gpsproxy.dev.domain.place.PoiSearchResult
import com.gpsproxy.dev.domain.place.PoiSearchSource
import com.gpsproxy.dev.domain.place.PoiSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingPlacesStore : PlacesStore {
    val historyEntries = mutableListOf<HistoryEntry>()
    val savedFavorites = mutableListOf<FavoritePlace>()
    val searchTerms = mutableListOf<String>()

    val favoritesFlow = MutableStateFlow<List<FavoritePlace>>(emptyList())
    val historyFlow = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val searchFlow = MutableStateFlow<List<String>>(emptyList())
    override val favorites: Flow<List<FavoritePlace>> get() = favoritesFlow
    override val history: Flow<List<HistoryEntry>> get() = historyFlow
    override val searchHistory: Flow<List<String>> get() = searchFlow

    override suspend fun addFavorite(name: String, address: String, point: GeoPoint, coord: CoordSystem) {
        savedFavorites += FavoritePlace(1L, name, address, point.lat, point.lng, coord, 0L, 0L)
    }

    override suspend fun renameFavorite(id: Long, newName: String) = Unit
    override suspend fun removeFavorite(id: Long) = Unit
    override suspend fun restoreFavorite(place: FavoritePlace) = Unit
    override suspend fun touchFavorite(id: Long) = Unit

    override suspend fun addHistory(name: String, address: String, point: GeoPoint) {
        historyEntries += HistoryEntry(1L, name, address, point.lat, point.lng, 0L)
    }

    override suspend fun clearHistory() = Unit

    override suspend fun addSearchTerm(term: String) {
        searchTerms += term
    }

    override suspend fun clearSearchTerms() = Unit
}

private class StubPoiSource : PoiSearchSource {
    override val hasLiveSource = false
    val queries = mutableListOf<PoiQuery>()
    override suspend fun search(query: PoiQuery): PoiSearchResult {
        queries += query
        return PoiSearchResult(emptyList(), PoiSource.LOCAL)
    }

    override suspend fun reverseGeocode(point: GeoPoint): String? = "地址"
}

class PlaceUseCasesTest {

    private val point = GeoPoint(30.25, 120.15)

    @Test
    fun `invalid point is rejected without touching the engine`() = runTest {
        val store = RecordingPlacesStore()
        val useCases = PlaceUseCases(engine = RecordingSimulationControl(), places = store)
        val result = useCases.applyPoint(GeoPoint(200.0, 120.0), "bad")
        assertTrue(result is Outcome.Failure)
        assertEquals(ErrorCode.COORD_0101, (result as Outcome.Failure).error.code)
        assertTrue(store.historyEntries.isEmpty())
    }

    @Test
    fun `history is recorded for confirmed points`() = runTest {
        val store = RecordingPlacesStore()
        val useCases = PlaceUseCases(engine = RecordingSimulationControl(), places = store)
        val result = useCases.applyPoint(point, "西湖", historyName = "西湖选点")
        assertTrue(result is Outcome.Success)
        assertEquals("西湖选点", store.historyEntries.single().name)
    }

    @Test
    fun `history can be skipped for route endpoints`() = runTest {
        val store = RecordingPlacesStore()
        val useCases = PlaceUseCases(engine = RecordingSimulationControl(), places = store)
        useCases.applyPoint(point, "终点", recordHistory = false)
        assertTrue(store.historyEntries.isEmpty())
    }

    @Test
    fun `search records term only on first page`() = runTest {
        val store = RecordingPlacesStore()
        val source = StubPoiSource()
        val search = PlaceUseCases.Search(source, store)
        search.search(PoiQuery("西湖", PoiCategory.ALL, point, false, 1000, 0), recordTerm = true)
        search.search(PoiQuery("西湖", PoiCategory.ALL, point, false, 1000, 1), recordTerm = true)
        assertEquals(listOf("西湖"), store.searchTerms)
        assertEquals(2, source.queries.size)
    }

    @Test
    fun `search skips blank terms`() = runTest {
        val store = RecordingPlacesStore()
        val search = PlaceUseCases.Search(StubPoiSource(), store)
        search.search(PoiQuery("", PoiCategory.ALL, point, true, 1000, 0), recordTerm = true)
        assertTrue(store.searchTerms.isEmpty())
    }
}

/** 只记录"是否被调用"的最小引擎替身。 */
private class RecordingSimulationControl : com.gpsproxy.dev.domain.simulation.port.SimulationControl {
    var selectedPoint: GeoPoint? = null
    var stopCalls = 0
    override fun initialize(point: GeoPoint?, address: String) = Unit
    override fun setPoint(point: GeoPoint, address: String) {
        selectedPoint = point
    }
    override fun stop() {
        stopCalls++
    }
    override fun nudge(northMeters: Double, eastMeters: Double) = Unit
    override fun setBlurRadius(meters: Int) = Unit
}
