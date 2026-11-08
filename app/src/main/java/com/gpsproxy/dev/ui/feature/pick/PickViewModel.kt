package com.gpsproxy.dev.ui.feature.pick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.geo.CoordTransform
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.core.messaging.MessageTone
import com.gpsproxy.dev.core.messaging.Messenger
import com.gpsproxy.dev.core.result.Outcome
import com.gpsproxy.dev.data.SettingsRepository
import com.gpsproxy.dev.domain.place.PlacesStore
import com.gpsproxy.dev.domain.place.usecase.PlaceUseCases
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import com.gpsproxy.dev.domain.simulation.usecase.StartResult
import com.gpsproxy.dev.domain.simulation.usecase.StartSimulationUseCase
import com.gpsproxy.dev.platform.service.SimulationSessionController
import com.gpsproxy.dev.ui.session.PlaceSelectionStore
import com.gpsproxy.dev.ui.session.RouteDraftStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 地图选点页 ViewModel。
 *
 * 选点草稿是跨屏状态（首页 → 选点页 → 巡航配置），因此放在
 * [PlaceSelectionStore]；本类只负责输入校验、逆地理编码与"确认后要做什么"。
 *
 * 历史记录只在用户确认选点时写入；巡航终点选择不写历史——这是旧实现的
 * 行为，之前靠调用方记得"别调错方法"来维持，现在由两个语义化方法固化。
 */
class PickViewModel(
    private val engine: com.gpsproxy.dev.domain.simulation.SimulationEngine,
    private val settingsRepo: SettingsRepository,
    private val selection: PlaceSelectionStore,
    private val routeDraft: RouteDraftStore,
    private val placeUseCases: PlaceUseCases,
    private val startUseCase: StartSimulationUseCase,
    private val session: SimulationSessionController,
    private val messenger: Messenger,
    private val places: PlacesStore,
    private val externalMap: com.gpsproxy.dev.platform.map.ExternalMapLauncher,
) : ViewModel() {

    val draftPoint: StateFlow<GeoPoint?> = selection.point
    val draftAddress: StateFlow<String> = selection.address
    val reverseAddress: StateFlow<String?> = selection.reverseAddress

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    fun prepareFromCurrent(state: SimulationState = engine.state.value) {
        val point = state.point ?: com.gpsproxy.dev.domain.place.DefaultLocation.POINT
        selection.prepare(point, state.address.ifBlank { com.gpsproxy.dev.domain.place.DefaultLocation.ADDRESS })
    }

    fun prepare(point: GeoPoint, address: String) {
        selection.prepare(point, address)
    }

    /**
     * 修改草稿点。
     *
     * @param address null 表示需要逆地理编码；结果返回时会检查选点是否已被再次修改，
     *                避免慢请求覆盖用户的新选择
     */
    fun setDraftPoint(point: GeoPoint, address: String?) {
        selection.setPoint(point, address)
        if (address != null) return
        viewModelScope.launch {
            _resolving.value = true
            val resolved = AppGraph.searchUseCase.reverseGeocode(point)
            if (selection.point.value == point) {
                selection.setReverseAddress(resolved)
            }
            _resolving.value = false
        }
    }

    fun nudge(northMeters: Double, eastMeters: Double) {
        val base = selection.point.value ?: return
        setDraftPoint(GeoUtils.offsetMeters(base, northMeters, eastMeters), null)
    }

    fun displayAddress(point: GeoPoint? = selection.point.value): String =
        selection.displayAddress(point)

    /** 输入框校验失败时的统一提示，避免界面直接拼错误码文案。 */
    fun reportInvalidCoordinate() {
        messenger.showError(ErrorCode.COORD_0101)
    }

    /** 剪贴板内容无法解析为坐标；与"超出范围"区分开，给出可用的格式示例。 */
    fun reportUnparsableCoordinate() {
        messenger.show({ place.unparsableCoords })
    }

    /** 剪贴板为空（或被系统限制读取）；与"格式不对"分开提示，避免误导。 */
    fun reportEmptyClipboard() {
        messenger.show({ place.clipboardEmpty })
    }

    /** 用外部地图预览草稿点。 */
    fun openInExternalMap() {
        val point = selection.point.value ?: return
        if (!externalMap.open(point, selection.displayAddress(point))) {
            messenger.show({ message.noMapApp })
        }
    }

    fun displayCoords(point: GeoPoint): String =
        CoordTransform.transform(point, CoordSystem.GCJ02, settings.value.coordSystem).formatted()

    /** 确认选点并开始单点模拟；启动失败不影响"选点已生效"。 */
    fun applyPickAsSimulation() {
        val point = selection.point.value
        if (point == null) {
            messenger.show({ place.pickFirst })
            return
        }
        if (!point.isValid()) {
            messenger.showError(ErrorCode.COORD_0101)
            return
        }
        viewModelScope.launch {
            val address = selection.displayAddress(point)
            val historyName = selection.address.value.ifBlank { address }
            when (val applied = placeUseCases.applyPoint(point, address, historyName)) {
                is Outcome.Failure -> messenger.showError(applied.error)
                is Outcome.Success -> {
                    when (val started = startUseCase.startSingle()) {
                        is StartResult.Started -> {
                            if (!session.startInjectionService()) {
                                messenger.showError(ErrorCode.INJ_0202)
                            }
                            session.startOverlayIfAllowed(settings.value.overlayEnabled)
                            messenger.show({ place.mockStarted }, MessageTone.SUCCESS)
                        }
                        is StartResult.Blocked -> messenger.showError(started.error)
                    }
                }
            }
        }
    }

    /** 巡航终点：只写草稿与选点，不写历史、不启动模拟。 */
    fun setRouteEnd(point: GeoPoint, address: String) {
        selection.setPoint(point, address)
        routeDraft.setEnd(point, address)
    }

    /**
     * 途经点：按进入选点页前记录的下标写入，-1 追加。
     *
     * 下标不放进导航参数，而是放在 [RouteDraftStore]：导航参数是字符串，
     * 而"待编辑第几个途经点"本质上属于草稿状态，放在一起才不会出现
     * 旋转屏幕/进程恢复后下标丢失的问题。
     */
    fun setRouteWaypoint(point: GeoPoint, address: String) {
        selection.setPoint(point, address)
        val index = routeDraft.consumePendingWaypointIndex()
        if (index == RouteDraftStore.ADD_WAYPOINT) {
            routeDraft.addWaypoint(point, address)
        } else {
            routeDraft.setWaypoint(index, point, address)
        }
    }

    fun saveDraftAsFavorite() {
        val point = selection.point.value ?: return
        val name = selection.address.value.ifBlank { selection.displayAddress(point) }
        viewModelScope.launch {
            places.addFavorite(name, selection.displayAddress(point), point, settings.value.coordSystem)
            messenger.show({ message.addedToFavorites }, MessageTone.SUCCESS)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PickViewModel(
                    engine = AppGraph.engine,
                    settingsRepo = AppGraph.settings,
                    selection = AppGraph.selection,
                    routeDraft = AppGraph.routeDraft,
                    placeUseCases = AppGraph.placeUseCases,
                    startUseCase = AppGraph.startSimulation,
                    session = AppGraph.session,
                    messenger = AppGraph.messenger,
                    places = AppGraph.places,
                    externalMap = AppGraph.externalMap,
                )
            }
        }
    }
}
