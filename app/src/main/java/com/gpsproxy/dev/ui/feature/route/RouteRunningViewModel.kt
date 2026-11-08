package com.gpsproxy.dev.ui.feature.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.messaging.MessageTone
import com.gpsproxy.dev.core.messaging.Messenger
import com.gpsproxy.dev.domain.simulation.SimulationEngine
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import com.gpsproxy.dev.platform.service.SimulationSessionController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 巡航运行页 ViewModel：只读进度 + 停止/暂停。 */
class RouteRunningViewModel(
    private val engine: SimulationEngine,
    private val session: SimulationSessionController,
    private val messenger: Messenger,
    private val exportTrack: com.gpsproxy.dev.domain.track.usecase.ExportTrackUseCase,
) : ViewModel() {

    val simulation: StateFlow<SimulationState> = engine.state

    fun stopRoute() {
        engine.stop()
        session.stopInjectionService()
        session.stopOverlay()
        messenger.show({ message.simulationStopped }, MessageTone.INFO)
    }

    fun nudge(northMeters: Double, eastMeters: Double) {
        engine.nudge(northMeters, eastMeters)
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

    fun setSpeedFactor(factor: Float) {
        engine.setRouteSpeedFactor(factor)
        val factorText = "%.2f".format(engine.state.value.speedFactor)
        messenger.show({ message.speedFactorChanged(factorText) })
    }

    fun seek(fraction: Float) {
        engine.seekRouteFraction(fraction)
    }

    fun reverse() {
        if (engine.reverseRoute()) {
            messenger.show({ message.routeDirectionSwitched }, MessageTone.INFO)
        }
    }

    fun restart() {
        if (engine.restartRoute()) {
            messenger.show({ message.routeReplayed }, MessageTone.SUCCESS)
        } else {
            messenger.show({ message.routeCannotReplay })
        }
    }

    /** 导出当前轨迹（未模糊的规划点）为 GPX。 */
    fun exportGpx(uri: String) {
        val state = engine.state.value
        val points = state.routePathPoints
        if (points.size < 2) {
            messenger.show({ message.noTrackToExport })
            return
        }
        viewModelScope.launch {
            val kind = state.route?.kind
            val strings = AppGraph.i18n.current
            val name = if (kind == null) {
                strings.message.defaultGpxName
            } else {
                strings.route.routeKindLabel(kind.name)
            }
            val elevations = List(points.size) { state.altitude }
            when (val result = exportTrack.export(uri, name, points, elevations)) {
                is com.gpsproxy.dev.core.result.Outcome.Success ->
                    messenger.show({ message.gpxExported }, MessageTone.SUCCESS)
                is com.gpsproxy.dev.core.result.Outcome.Failure ->
                    messenger.showError(result.error)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RouteRunningViewModel(
                    engine = AppGraph.engine,
                    session = AppGraph.session,
                    messenger = AppGraph.messenger,
                    exportTrack = AppGraph.exportTrack,
                )
            }
        }
    }
}
