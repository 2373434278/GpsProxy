package com.gpsproxy.dev.ui.feature.diagnostic

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.BuildConfig
import com.gpsproxy.dev.core.device.PermissionUtils
import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.log.LogBus
import com.gpsproxy.dev.core.log.LogEntry
import com.gpsproxy.dev.core.messaging.MessageTone
import com.gpsproxy.dev.core.messaging.Messenger
import com.gpsproxy.dev.data.SettingsRepository
import com.gpsproxy.dev.domain.container.ContainerRepository
import com.gpsproxy.dev.domain.diagnostics.DiagnosticsReportBuilder
import com.gpsproxy.dev.domain.diagnostics.DiagnosticsSnapshot
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.simulation.SimulationEngine
import com.gpsproxy.dev.domain.simulation.model.ChannelState
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import com.gpsproxy.dev.platform.diagnostics.DiagnosticsExporter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 诊断页 ViewModel。
 *
 * 报告内容由纯函数 [DiagnosticsReportBuilder] 生成，导出由
 * [DiagnosticsExporter] 完成；这里只负责在导出时抓一份一致快照。
 * 因此"报告里有什么/没有什么（脱敏）"可以用单测覆盖，而不需要跑 Android。
 */
class DiagnosticsViewModel(
    application: Application,
    private val engine: SimulationEngine,
    private val containerRepo: ContainerRepository,
    settingsRepo: SettingsRepository,
    private val builder: DiagnosticsReportBuilder,
    private val exporter: DiagnosticsExporter,
    private val messenger: Messenger,
) : AndroidViewModel(application) {

    val channels: StateFlow<List<ChannelState>> = engine.channels
    val simulation: StateFlow<SimulationState> = engine.state
    val logs: StateFlow<List<LogEntry>> = LogBus.entries

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun clearLogs() {
        LogBus.clear()
    }

    fun exportDiagnostics() {
        viewModelScope.launch {
            val containerCount = runCatching { containerRepo.listApps()?.size ?: 0 }.getOrDefault(0)
            val snapshot = DiagnosticsSnapshot(
                generatedAtMillis = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                buildType = BuildConfig.BUILD_TYPE,
                manufacturer = Build.MANUFACTURER ?: "",
                model = Build.MODEL ?: "",
                apiLevel = Build.VERSION.SDK_INT,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
                settings = settings.value,
                simulation = engine.state.value,
                channels = engine.channels.value,
                containerAvailable = AppGraph.container.available,
                containerAppCount = containerCount,
                mockLocationAllowed = PermissionUtils.isMockLocationAllowed(getApplication()),
                logs = LogBus.snapshot(),
            )
            val fileName = exporter.export(builder.build(snapshot))
            if (fileName == null) {
                messenger.showError(ErrorCode.DIAG_0101)
            } else {
                messenger.show({ message.diagnosticsExported(fileName) }, MessageTone.SUCCESS)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                DiagnosticsViewModel(
                    application = app,
                    engine = AppGraph.engine,
                    containerRepo = AppGraph.containerApps,
                    settingsRepo = AppGraph.settings,
                    builder = AppGraph.diagnosticsBuilder,
                    exporter = AppGraph.diagnosticsExporter,
                    messenger = AppGraph.messenger,
                )
            }
        }
    }
}
