package com.gpsproxy.dev.ui.feature.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.messaging.MessageTone
import com.gpsproxy.dev.core.messaging.Messenger
import com.gpsproxy.dev.domain.simulation.route.RouteStore
import com.gpsproxy.dev.domain.simulation.route.SavedRoute
import com.gpsproxy.dev.ui.session.RouteDraftStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 路线库页 ViewModel：只做列表与草稿之间的搬运，不做轨迹规划。 */
class RoutesViewModel(
    private val routeStore: RouteStore,
    private val draftStore: RouteDraftStore,
    private val messenger: Messenger,
) : ViewModel() {

    val routes: StateFlow<List<SavedRoute>> = routeStore.routes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 载入只填充草稿，由用户在配置页确认后开始。 */
    fun load(route: SavedRoute) {
        draftStore.loadSavedRoute(route)
        viewModelScope.launch { routeStore.touch(route.id) }
        messenger.show({ message.routeLoaded(route.name) }, MessageTone.SUCCESS)
    }

    fun toggleFavorite(route: SavedRoute) {
        viewModelScope.launch {
            runCatching { routeStore.setFavorite(route.id, !route.favorite) }
                .onSuccess {
                    messenger.show(
                        {
                            if (route.favorite) message.routeUnfavorited else message.routeFavoriteAdded
                        },
                        MessageTone.SUCCESS,
                    )
                }
                .onFailure { messenger.show({ message.routeUpdateFailed }) }
        }
    }

    fun rename(route: SavedRoute, name: String) {
        viewModelScope.launch {
            runCatching { routeStore.rename(route.id, name) }
                .onSuccess { messenger.show({ message.routeRenamed }, MessageTone.SUCCESS) }
                .onFailure { messenger.show({ message.routeRenameFailed }) }
        }
    }

    fun remove(route: SavedRoute) {
        viewModelScope.launch {
            runCatching { routeStore.remove(route.id) }
                .onSuccess { messenger.show({ message.routeDeleted(route.name) }, MessageTone.INFO) }
                .onFailure { messenger.show({ message.routeDeleteFailed }) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RoutesViewModel(
                    routeStore = AppGraph.routes,
                    draftStore = AppGraph.routeDraft,
                    messenger = AppGraph.messenger,
                )
            }
        }
    }
}
