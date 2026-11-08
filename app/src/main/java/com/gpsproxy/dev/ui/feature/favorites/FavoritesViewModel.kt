package com.gpsproxy.dev.ui.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.messaging.MessageTone
import com.gpsproxy.dev.core.messaging.Messenger
import com.gpsproxy.dev.domain.place.FavoritePlace
import com.gpsproxy.dev.domain.place.HistoryEntry
import com.gpsproxy.dev.domain.place.PlacesStore
import com.gpsproxy.dev.domain.place.usecase.PlaceUseCases
import com.gpsproxy.dev.domain.simulation.usecase.StartResult
import com.gpsproxy.dev.domain.simulation.usecase.StartSimulationUseCase
import com.gpsproxy.dev.platform.service.SimulationSessionController
import com.gpsproxy.dev.ui.session.PlaceSelectionStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 收藏与历史页 ViewModel。
 *
 * "应用收藏 → 立即开始模拟"是产品既有行为，统一走用例，避免这一路径
 * 忘记启动前台服务或悬浮球。
 */
class FavoritesViewModel(
    private val places: PlacesStore,
    private val placeUseCases: PlaceUseCases,
    private val startUseCase: StartSimulationUseCase,
    private val session: SimulationSessionController,
    private val messenger: Messenger,
    selection: PlaceSelectionStore,
    private val settingsProvider: () -> com.gpsproxy.dev.domain.settings.AppSettings,
) : ViewModel() {

    val favorites: StateFlow<List<FavoritePlace>> = places.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<HistoryEntry>> = places.history
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val mapCenter: StateFlow<GeoPoint> = selection.mapCenter

    fun applyFavorite(favorite: FavoritePlace) {
        viewModelScope.launch {
            placeUseCases.applyFavorite(favorite)
            startAfterSelection()
        }
    }

    fun applyHistory(entry: HistoryEntry) {
        viewModelScope.launch {
            placeUseCases.applyHistory(entry)
            startAfterSelection()
        }
    }

    fun removeFavorite(id: Long) {
        viewModelScope.launch { places.removeFavorite(id) }
    }

    fun restoreFavorite(place: FavoritePlace) {
        viewModelScope.launch { places.restoreFavorite(place) }
    }

    fun renameFavorite(id: Long, name: String) {
        viewModelScope.launch { places.renameFavorite(id, name) }
    }

    fun clearHistory() {
        viewModelScope.launch { places.clearHistory() }
    }

    private suspend fun startAfterSelection() {
        when (val result = startUseCase.startSingle()) {
            is StartResult.Started -> {
                session.startInjectionService()
                session.startOverlayIfAllowed(settingsProvider().overlayEnabled)
                messenger.show({ place.mockStarted }, MessageTone.SUCCESS)
            }
            is StartResult.Blocked -> messenger.showError(result.error)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FavoritesViewModel(
                    places = AppGraph.places,
                    placeUseCases = AppGraph.placeUseCases,
                    startUseCase = AppGraph.startSimulation,
                    session = AppGraph.session,
                    messenger = AppGraph.messenger,
                    selection = AppGraph.selection,
                    settingsProvider = { AppGraph.currentSettings() },
                )
            }
        }
    }
}
