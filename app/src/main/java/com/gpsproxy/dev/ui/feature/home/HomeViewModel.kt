package com.gpsproxy.dev.ui.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.device.PermissionSnapshot
import com.gpsproxy.dev.core.device.PermissionUtils
import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.geo.CoordTransform
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.messaging.MessageTone
import com.gpsproxy.dev.core.messaging.Messenger
import com.gpsproxy.dev.data.SettingsRepository
import com.gpsproxy.dev.domain.place.DefaultLocation
import com.gpsproxy.dev.domain.place.FavoritePlace
import com.gpsproxy.dev.domain.place.PlacesStore
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.simulation.SimulationEngine
import com.gpsproxy.dev.domain.simulation.model.ChannelState
import com.gpsproxy.dev.domain.simulation.model.ChannelStatus
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import com.gpsproxy.dev.domain.simulation.usecase.StartBlockAction
import com.gpsproxy.dev.domain.simulation.usecase.StartResult
import com.gpsproxy.dev.domain.simulation.usecase.StartSimulationUseCase
import com.gpsproxy.dev.platform.service.SimulationSessionController
import com.gpsproxy.dev.ui.components.StatusTone
import com.gpsproxy.dev.ui.session.PlaceSelectionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 首页派生状态。
 *
 * 旧实现把状态 tone、地址回退、"是否还需要授权"这些业务判断写在 Composable 里，
 * 导致同一个判断（例如"当前是否算已注入"）在首页与悬浮面板里各写一遍。
 * 现在这些判断由 ViewModel 统一产出，界面只负责渲染。
 */
enum class HomeStatus { NOT_STARTED, PAUSED, ROUTE_COMPLETED, CHANNEL_MISS, ROUTE_ACTIVE, WALKING, ACTIVE }

data class HomeUiState(
    val point: GeoPoint = DefaultLocation.POINT,
    val markerPoint: GeoPoint = DefaultLocation.POINT,
    val address: String = DefaultLocation.ADDRESS,
    val coordLabel: String = CoordSystem.GCJ02.label,
    val displayCoords: String = "",
    val status: HomeStatus = HomeStatus.NOT_STARTED,
    val statusTone: StatusTone = StatusTone.Idle,
    val isRoute: Boolean = false,
    val injected: Boolean = false,
    val paused: Boolean = false,
    val isFavorite: Boolean = false,
    val okChannels: Int = 0,
    val channelTotal: Int = 0,
    val accuracyMeters: Int = 5,
    val speedKmh: Double = 0.0,
    val altitudeMeters: Double = 12.0,
    val blurRadiusMeters: Int = 0,
    val blurEnabled: Boolean = false,
    val channel: InjectionChannel = InjectionChannel.CONTAINER,
    val walkSpeedKmh: Int = 5,
    val walking: Boolean = false,
    val autoStopAtMillis: Long = 0L,
)

/**
 * 首页 ViewModel：模拟控制 + 当前选点展示。
 *
 * 启动前置条件不再在这里判断，而是交给 [StartSimulationUseCase]；
 * 被阻断时只把 [StartBlockAction] 交给界面决定跳转，从而消除
 * "同样的规则在首页与巡航页各写一遍"的问题。
 */
class HomeViewModel(
    application: Application,
    private val engine: SimulationEngine,
    private val settingsRepo: SettingsRepository,
    private val places: PlacesStore,
    private val selection: PlaceSelectionStore,
    private val startUseCase: StartSimulationUseCase,
    private val session: SimulationSessionController,
    private val messenger: Messenger,
    private val externalMap: com.gpsproxy.dev.platform.map.ExternalMapLauncher,
) : AndroidViewModel(application) {

    val simulation: StateFlow<SimulationState> = engine.state
    val channels: StateFlow<List<ChannelState>> = engine.channels

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val favorites = places.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _permissions = MutableStateFlow(PermissionUtils.snapshot(application))
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    private val _blockAction = MutableStateFlow<StartBlockAction?>(null)
    val blockAction: StateFlow<StartBlockAction?> = _blockAction.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        engine.state,
        settings,
        _permissions,
        favorites,
        engine.channels,
    ) { state, settingsValue, permissions, favorites, channels ->
        buildUiState(state, settingsValue, permissions, favorites, channels)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    fun refresh() {
        _permissions.value = PermissionUtils.snapshot(getApplication())
        engine.refreshChannels()
    }

    fun onMapCenterChanged(point: GeoPoint) {
        selection.setMapCenter(point)
    }

    fun preparePickFromCurrent() {
        val state = engine.state.value
        val point = state.point ?: DefaultLocation.POINT
        selection.prepare(point, state.address.ifBlank { DefaultLocation.ADDRESS })
    }

    fun displayCoords(point: GeoPoint): String =
        CoordTransform.transform(point, CoordSystem.GCJ02, settings.value.coordSystem).formatted()

    fun startSimulation() {
        viewModelScope.launch {
            when (val result = startUseCase.startSingle()) {
                is StartResult.Started -> {
                    if (!session.startInjectionService()) {
                        messenger.showError(ErrorCode.INJ_0202)
                    }
                    session.startOverlayIfAllowed(settings.value.overlayEnabled)
                    val channelName = settings.value.injectionChannel.name
                    messenger.show(
                        text = { message.startSuccess(channelName) },
                        tone = MessageTone.SUCCESS,
                    )
                }
                is StartResult.Blocked -> {
                    // NONE 表示没有可去的页面（例如状态机拒绝），弹设置引导只会误导用户。
                    if (result.action != StartBlockAction.NONE) {
                        _blockAction.value = result.action
                    }
                    messenger.showError(result.error)
                }
            }
        }
    }

    fun stopSimulation() {
        engine.stop()
        session.stopInjectionService()
        session.stopOverlay()
        messenger.show({ message.simulationStopped }, MessageTone.INFO)
    }

    fun toggleSimulation() {
        if (engine.state.value.isActive) stopSimulation() else startSimulation()
    }

    fun pauseOrResume() {
        when (engine.state.value.runState) {
            RunState.RUNNING -> {
                engine.pause()
                messenger.show({ message.injectionPaused })
            }
            RunState.PAUSED -> {
                engine.resume()
                messenger.show({ message.injectionResumed }, MessageTone.SUCCESS)
            }
            else -> Unit
        }
    }

    fun nudge(northMeters: Double, eastMeters: Double) {
        engine.nudge(northMeters, eastMeters)
    }

    /** 摇杆按住行走：方向 + 速度（m/s）。 */
    fun walk(bearingDeg: Float, speedMps: Float) {
        engine.setManualMotion(com.gpsproxy.dev.domain.simulation.model.MotionVector(bearingDeg, speedMps))
    }

    fun stopWalk() {
        engine.setManualMotion(null)
    }

    fun setWalkSpeed(kmh: Int) {
        viewModelScope.launch { settingsRepo.setWalkSpeed(kmh) }
    }

    /** 用外部地图预览当前点；没有地图应用时给出提示。 */
    fun openInExternalMap() {
        val state = engine.state.value
        val point = state.point ?: return
        if (!externalMap.open(point, state.address.ifBlank { point.formatted() })) {
            messenger.show({ message.noMapApp })
        }
    }

    fun notifyCopied() {
        messenger.show({ message.coordsCopied }, MessageTone.SUCCESS)
    }

    fun toggleFavoriteCurrent() {
        val state = engine.state.value
        val point = state.point ?: return
        val coords = point.formatted()
        val name = if (state.mode == SimMode.ROUTE) {
            AppGraph.i18n.current.message.favoriteName(coords)
        } else {
            state.address.ifBlank { coords }
        }
        viewModelScope.launch {
            places.addFavorite(name, state.address, point, settings.value.coordSystem)
            messenger.show({ message.addedToFavorites }, MessageTone.SUCCESS)
        }
    }

    /** 消费一次阻断引导，避免配置变化时对话框反复弹出。 */
    fun consumeBlockAction() {
        _blockAction.value = null
    }

    private fun buildUiState(
        state: SimulationState,
        settings: AppSettings,
        permissions: PermissionSnapshot,
        favorites: List<FavoritePlace>,
        channels: List<ChannelState>,
    ): HomeUiState {
        val point = state.point ?: DefaultLocation.POINT
        // 运行中地图标记跟的是应用真正读到的坐标（模糊后的注入点）；
        // 未开始时仍显示用户选点，避免开启模糊后地图先跳到偏移位置造成困惑。
        val marker = if (state.mode != SimMode.IDLE) state.injectedPoint ?: point else point
        val injected = state.mode != SimMode.IDLE
        val (tone, status) = when {
            state.runState == RunState.PAUSED -> StatusTone.Warn to HomeStatus.PAUSED
            state.runState == RunState.COMPLETED -> StatusTone.Active to HomeStatus.ROUTE_COMPLETED
            state.runState == RunState.ERROR -> StatusTone.Error to HomeStatus.CHANNEL_MISS
            state.mode == SimMode.ROUTE && state.runState == RunState.RUNNING ->
                StatusTone.Active to HomeStatus.ROUTE_ACTIVE
            state.walking -> StatusTone.Active to HomeStatus.WALKING
            injected -> StatusTone.Active to HomeStatus.ACTIVE
            else -> StatusTone.Idle to HomeStatus.NOT_STARTED
        }
        val isFavorite = favorites.any {
            abs(it.lat - point.lat) < COORD_EPSILON && abs(it.lng - point.lng) < COORD_EPSILON
        }
        return HomeUiState(
            point = point,
            markerPoint = marker,
            address = state.address.ifBlank { DefaultLocation.ADDRESS },
            coordLabel = settings.coordSystem.label,
            displayCoords = CoordTransform.transform(marker, CoordSystem.GCJ02, settings.coordSystem).formatted(),
            status = status,
            statusTone = tone,
            isRoute = state.mode == SimMode.ROUTE,
            injected = injected,
            paused = state.runState == RunState.PAUSED,
            isFavorite = isFavorite,
            okChannels = channels.count { it.status == ChannelStatus.OK },
            channelTotal = channels.size,
            accuracyMeters = settings.accuracyMeters,
            speedKmh = state.speedKmh,
            altitudeMeters = state.altitude,
            blurRadiusMeters = state.blurRadiusMeters,
            blurEnabled = settings.blurEnabled,
            channel = state.injectionChannel ?: settings.injectionChannel,
            walkSpeedKmh = settings.walkSpeedKmh,
            walking = state.walking,
            autoStopAtMillis = state.autoStopAtMillis,
        )
    }

    companion object {
        private const val COORD_EPSILON = 1e-6

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                HomeViewModel(
                    application = app,
                    engine = AppGraph.engine,
                    settingsRepo = AppGraph.settings,
                    places = AppGraph.places,
                    selection = AppGraph.selection,
                    startUseCase = AppGraph.startSimulation,
                    session = AppGraph.session,
                    messenger = AppGraph.messenger,
                    externalMap = AppGraph.externalMap,
                )
            }
        }
    }
}
