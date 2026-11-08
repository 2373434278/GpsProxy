package com.gpsproxy.dev.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.BuildConfig
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.i18n.AppLanguage
import com.gpsproxy.dev.core.log.LogBus
import com.gpsproxy.dev.core.messaging.MessageTone
import com.gpsproxy.dev.core.messaging.Messenger
import com.gpsproxy.dev.data.SettingsRepository
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.settings.DetailLevel
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.settings.NotificationDetail
import com.gpsproxy.dev.platform.service.SimulationSessionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel。
 *
 * 每一项设置都是"立即持久化 + 立即影响运行时"：例如关闭悬浮球会同步停掉服务，
 * 改模糊半径会立即重算注入点。这些副作用集中在 ViewModel，
 * 避免界面点了开关却要等下一次启动才生效。
 */
class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val session: SimulationSessionController,
    private val messenger: Messenger,
    private val containerAvailableCheck: () -> Boolean,
    private val hasLivePoiSource: Boolean,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AppSettings(language = AppGraph.i18n.setting),
        )

    private val _containerAvailable = MutableStateFlow(containerAvailableCheck())
    val containerAvailable: StateFlow<Boolean> = _containerAvailable.asStateFlow()

    val poiLiveSource: Boolean = hasLivePoiSource
    val appVersion: String = BuildConfig.VERSION_NAME
    val buildType: String = BuildConfig.BUILD_TYPE

    fun refreshAvailability() {
        _containerAvailable.value = containerAvailableCheck()
    }

    fun setCoordSystem(value: CoordSystem) {
        viewModelScope.launch { settingsRepo.setCoordSystem(value) }
    }
    fun setAccuracy(value: Int) {
        viewModelScope.launch { settingsRepo.setAccuracy(value) }
    }
    fun setDrift(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDrift(enabled) }
    }
    fun setBlurEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setBlurEnabled(enabled) }
    }
    fun setBlurRadius(meters: Int) {
        viewModelScope.launch { settingsRepo.setBlurRadius(meters) }
    }
    fun setUpdateInterval(ms: Int) {
        viewModelScope.launch { settingsRepo.setUpdateInterval(ms) }
    }
    fun setWalkSpeed(kmh: Int) {
        viewModelScope.launch { settingsRepo.setWalkSpeed(kmh) }
    }
    fun setReportSpeed(kmh: Int) {
        viewModelScope.launch { settingsRepo.setReportSpeed(kmh) }
    }
    fun setReportBearing(degrees: Int) {
        viewModelScope.launch { settingsRepo.setReportBearing(degrees) }
    }
    fun setAltitude(meters: Int) {
        viewModelScope.launch { settingsRepo.setAltitude(meters) }
    }
    fun setAutoStop(minutes: Int) {
        viewModelScope.launch { settingsRepo.setAutoStop(minutes) }
    }
    fun setDetailLevel(level: DetailLevel) {
        viewModelScope.launch { settingsRepo.setDetailLevel(level) }
    }
    fun setInjectionChannel(channel: InjectionChannel) {
        viewModelScope.launch { settingsRepo.setInjectionChannel(channel) }
    }
    fun setNotificationDetail(detail: NotificationDetail) {
        viewModelScope.launch { settingsRepo.setNotificationDetail(detail) }
    }

    fun setLanguage(value: AppLanguage) {
        viewModelScope.launch {
            settingsRepo.setLanguage(value)
            // 立即生效，不等 DataStore 回环；持久化仍是唯一真源。
            AppGraph.i18n.apply(value)
        }
    }
    fun setBootRestore(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setBootRestore(enabled) }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setOverlayEnabled(enabled)
            if (!enabled) {
                session.stopOverlay()
            } else if (AppGraph.engine.state.value.isActive) {
                session.startOverlayIfAllowed(true)
            }
        }
    }

    fun clearLogs() {
        LogBus.clear()
        messenger.show({ message.logsCleared }, MessageTone.SUCCESS)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepo = AppGraph.settings,
                    session = AppGraph.session,
                    messenger = AppGraph.messenger,
                    containerAvailableCheck = { AppGraph.container.available },
                    hasLivePoiSource = AppGraph.poi.hasLiveSource,
                )
            }
        }
    }
}
