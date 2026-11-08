package com.gpsproxy.dev.ui.feature.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.messaging.MessageTone
import com.gpsproxy.dev.core.messaging.Messenger
import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.domain.place.DefaultLocation
import com.gpsproxy.dev.domain.simulation.SimulationEngine
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.domain.simulation.model.TravelMode
import com.gpsproxy.dev.domain.simulation.route.RouteHistoryPolicy
import com.gpsproxy.dev.domain.simulation.route.SavedRoute
import com.gpsproxy.dev.domain.simulation.usecase.StartResult
import com.gpsproxy.dev.domain.simulation.usecase.StartSimulationUseCase
import com.gpsproxy.dev.platform.service.SimulationSessionController
import com.gpsproxy.dev.ui.session.PlaceSelectionStore
import com.gpsproxy.dev.ui.session.RouteDraft
import com.gpsproxy.dev.ui.session.RouteDraftStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 巡航配置页 ViewModel。
 *
 * 草稿的互斥规则（循环 / 往返）在 [RouteDraftStore] 里，本类只做 UI 动作转发；
 * 启动仍走统一用例，因此"容器未就绪 / 未授权"的提示与首页完全一致。
 */
class RouteViewModel(
    private val engine: SimulationEngine,
    private val store: RouteDraftStore,
    private val selection: PlaceSelectionStore,
    private val startUseCase: StartSimulationUseCase,
    private val session: SimulationSessionController,
    private val messenger: Messenger,
    private val settingsProvider: () -> com.gpsproxy.dev.domain.settings.AppSettings,
    private val routeStore: com.gpsproxy.dev.domain.simulation.route.RouteStore,
    private val importTrack: com.gpsproxy.dev.domain.track.usecase.ImportTrackUseCase,
) : ViewModel() {

    val draft: StateFlow<RouteDraft> = store.draft

    /** 当前是否有会话在运行：运行中不允许再开巡航，避免按钮点了没反应。 */
    val sessionActive: StateFlow<Boolean> = engine.state
        .map { it.isActive }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), engine.state.value.isActive)

    /** 进入页面时把当前模拟位置作为默认起点（不覆盖用户已设置的起点）。 */
    fun initializeStart() {
        val state = engine.state.value
        store.initializeStart(
            point = state.point ?: DefaultLocation.POINT,
            address = state.address.ifBlank { DefaultLocation.ADDRESS },
        )
    }

    fun setStartToCurrent() {
        val state = engine.state.value
        store.setStart(
            point = state.point ?: DefaultLocation.POINT,
            address = state.address.ifBlank { DefaultLocation.ADDRESS },
        )
        messenger.show({ message.routeStartPointSet }, MessageTone.SUCCESS)
    }

    fun setMode(mode: TravelMode) {
        store.setMode(mode)
    }

    fun setSpeed(speedKmh: Int) {
        store.setSpeed(speedKmh)
    }

    fun setLoop(enabled: Boolean) {
        store.setLoop(enabled)
    }

    fun setRoundTrip(enabled: Boolean) {
        store.setRoundTrip(enabled)
    }

    fun setKind(kind: RouteKind) {
        store.setKind(kind)
    }

    fun setRoamRadius(meters: Int) {
        store.setRoamRadius(meters)
    }

    /** 添加途经点：记录 -1 表示追加，然后跳地图选点。 */
    fun prepareAddWaypoint() {
        store.prepareWaypointPick(RouteDraftStore.ADD_WAYPOINT)
    }

    fun prepareEditWaypoint(index: Int) {
        store.prepareWaypointPick(index)
    }

    fun removeWaypoint(index: Int) {
        store.removeWaypoint(index)
    }

    fun moveWaypoint(index: Int, delta: Int) {
        store.moveWaypoint(index, delta)
    }

    /** 把当前草稿记录为收藏路线（同签名重复收藏会合并为一条）。 */
    fun saveCurrentAsFavorite() {
        val config = store.draft.value.toConfig()
        if (config == null) {
            messenger.show({ message.routeIncomplete })
            return
        }
        viewModelScope.launch {
            runCatching {
                routeStore.saveFavorite(config, RouteHistoryPolicy.defaultName(config))
            }.onSuccess {
                messenger.show({ message.routeFavorited }, MessageTone.SUCCESS)
            }.onFailure {
                messenger.show({ message.routeSaveFailed })
            }
        }
    }

    /** 从 SAF URI 导入 GPX；成功后自动切换到"导入轨迹"并以首尾点为起终点。 */
    fun importGpx(uri: String) {
        viewModelScope.launch {
            when (val result = importTrack.import(uri)) {
                is com.gpsproxy.dev.core.result.Outcome.Success -> {
                    val track = result.value
                    store.setImportedTrack(track.points, track.name)
                    val count = track.points.size
                    messenger.show({ message.trackImported(count) }, MessageTone.SUCCESS)
                }
                is com.gpsproxy.dev.core.result.Outcome.Failure -> messenger.showError(result.error)
            }
        }
    }

    /** 选终点前把当前草稿同步到选点页，避免打开地图时显示旧点。 */
    fun preparePickEnd() {
        val draft = store.draft.value
        selection.prepare(
            point = draft.end ?: draft.start ?: DefaultLocation.POINT,
            address = draft.endAddress,
        )
    }

    fun startRoute(onResult: (Boolean) -> Unit) {
        val draft = store.draft.value
        val config = draft.toConfig()
        if (config == null) {
            messenger.show({
                when (draft.kind) {
                    RouteKind.ROAM -> message.routeNeedRoamCenter
                    RouteKind.IMPORTED -> message.routeNeedGpx
                    RouteKind.POINT_TO_POINT -> message.routeNeedEndpoints
                }
            })
            onResult(false)
            return
        }
        // 按钮在运行中会置灰，这里兜住并发/误触：不要依赖状态机报一个笼统错误。
        if (engine.state.value.isActive) {
            messenger.show({ message.routeAlreadyRunning })
            onResult(false)
            return
        }
        viewModelScope.launch {
            when (val result = startUseCase.startRoute(config)) {                is StartResult.Started -> {
                    if (!session.startInjectionService()) {
                        messenger.showError(ErrorCode.INJ_0202)
                    }
                    session.startOverlayIfAllowed(settingsProvider().overlayEnabled)
                    messenger.show({ message.routeStarted }, MessageTone.SUCCESS)
                    onResult(true)
                }
                is StartResult.Blocked -> {
                    messenger.showError(result.error)
                    onResult(false)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RouteViewModel(
                    engine = AppGraph.engine,
                    store = AppGraph.routeDraft,
                    selection = AppGraph.selection,
                    startUseCase = AppGraph.startSimulation,
                    session = AppGraph.session,
                    messenger = AppGraph.messenger,
                    settingsProvider = { AppGraph.currentSettings() },
                    routeStore = AppGraph.routes,
                    importTrack = AppGraph.importTrack,
                )
            }
        }
    }
}
