package com.gpsproxy.dev.ui.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.domain.place.PlacesStore
import com.gpsproxy.dev.domain.place.Poi
import com.gpsproxy.dev.domain.place.PoiCategory
import com.gpsproxy.dev.domain.place.PoiQuery
import com.gpsproxy.dev.domain.place.PoiSource
import com.gpsproxy.dev.domain.place.usecase.PlaceUseCases
import com.gpsproxy.dev.ui.session.PlaceSelectionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchTab {
    KEYWORD,
    NEARBY,
    HISTORY,
}

data class SearchUiState(
    val query: String = "",
    val tab: SearchTab = SearchTab.KEYWORD,
    val category: PoiCategory = PoiCategory.ALL,
    val radiusMeters: Int = 1000,
    val loading: Boolean = false,
    val items: List<Poi> = emptyList(),
    val source: PoiSource? = null,
    val warning: String? = null,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
)

/**
 * 搜索页 ViewModel。
 *
 * 查询/分页/防抖属于表现层交互，留在 UI 层；"记录搜索词"的规则在用例里，
 * 这样以后把搜索嵌入别的入口时不会出现两套历史记录规则。
 */
class SearchViewModel(
    private val searchUseCase: PlaceUseCases.Search,
    private val places: PlacesStore,
    private val selection: PlaceSelectionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val mapCenter: StateFlow<GeoPoint> = selection.mapCenter
    val searchHistory: StateFlow<List<String>> = places.searchHistory
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var searchJob: Job? = null

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update {
                it.copy(items = emptyList(), loading = false, warning = null, error = null, source = null)
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(reset = true)
        }
    }

    fun setTab(tab: SearchTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == SearchTab.HISTORY) {
            _state.update { it.copy(items = emptyList(), loading = false, page = 0, hasMore = false) }
        } else {
            viewModelScope.launch { runSearch(reset = true) }
        }
    }

    fun setCategory(category: PoiCategory) {
        _state.update { it.copy(category = category) }
        viewModelScope.launch { runSearch(reset = true) }
    }

    fun setRadius(radiusMeters: Int) {
        _state.update { it.copy(radiusMeters = radiusMeters) }
        viewModelScope.launch { runSearch(reset = true) }
    }

    fun submit() {
        searchJob?.cancel()
        viewModelScope.launch { runSearch(reset = true) }
    }

    fun loadMore() {
        val state = _state.value
        if (state.loading || !state.hasMore) return
        viewModelScope.launch { runSearch(reset = false) }
    }

    fun clearHistory() {
        viewModelScope.launch { places.clearSearchTerms() }
    }

    /** 选中 POI 后写入跨屏选点草稿，由界面负责导航到地图页。 */
    fun selectPoi(poi: Poi) {
        selection.prepare(GeoPoint(poi.lat, poi.lng), poi.name)
    }

    private suspend fun runSearch(reset: Boolean) {
        val state = _state.value
        if (state.tab == SearchTab.HISTORY) return
        if (state.query.isBlank() && state.tab != SearchTab.NEARBY) {
            _state.update { it.copy(items = emptyList(), loading = false) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        val page = if (reset) 0 else state.page + 1
        val result = runCatching {
            searchUseCase.search(
                query = PoiQuery(
                    query = state.query,
                    category = state.category,
                    center = mapCenter.value,
                    nearby = state.tab == SearchTab.NEARBY,
                    radiusMeters = state.radiusMeters,
                    page = page,
                ),
                recordTerm = reset,
            )
        }
        result.onSuccess { response ->
            _state.update {
                it.copy(
                    loading = false,
                    items = if (reset) response.items else it.items + response.items,
                    source = response.source,
                    warning = response.warning,
                    error = null,
                    page = page,
                    hasMore = response.hasMore,
                )
            }
        }.onFailure {
            _state.update {
                it.copy(
                    loading = false,
                    error = StringsHolder.text { place.searchError },
                    items = if (reset) emptyList() else it.items,
                )
            }
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    searchUseCase = AppGraph.searchUseCase,
                    places = AppGraph.places,
                    selection = AppGraph.selection,
                )
            }
        }
    }
}
