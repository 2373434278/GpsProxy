package com.gpsproxy.dev.ui.feature.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.device.DeviceCapability
import com.gpsproxy.dev.core.device.DeviceCapabilityChecker
import com.gpsproxy.dev.domain.selfcheck.SelfCheckRunner
import com.gpsproxy.dev.domain.selfcheck.SelfCheckStep
import com.gpsproxy.dev.domain.selfcheck.StepState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 引导流程（免责声明 → 引擎自检 → 首页）的共享状态。
 *
 * 三个页面属于同一条流程，共用一个 ViewModel：
 * - 设备能力只探测一次；
 * - 自检结果在离开引擎页后仍然保留（返回时不用重跑）；
 * - 免责声明同意状态只有一个来源。
 *
 * 自检是真实探测，没有人为延迟；页面可以在结果逐项到达时做动画。
 */
class StartupViewModel(
    private val selfCheckRunner: SelfCheckRunner,
    private val capabilityChecker: () -> DeviceCapability = { DeviceCapabilityChecker.check() },
) : ViewModel() {

    val capability: DeviceCapability = capabilityChecker()

    val steps: StateFlow<List<SelfCheckStep>> = selfCheckRunner.steps
    val running: StateFlow<Boolean> = selfCheckRunner.running
    val allPassed: StateFlow<Boolean?> = selfCheckRunner.allPassed

    private val _disclaimerAccepted = MutableStateFlow(false)
    val disclaimerAccepted: StateFlow<Boolean> = _disclaimerAccepted.asStateFlow()

    /** 容器运行时是否可用，用于引擎页的降级提示。 */
    private val _containerAvailable = MutableStateFlow(AppGraph.container.available)
    val containerAvailable: StateFlow<Boolean> = _containerAvailable.asStateFlow()

    init {
        viewModelScope.launch {
            _disclaimerAccepted.value = AppGraph.settings.settings.first().disclaimerAccepted
        }
    }

    fun acceptDisclaimer() {
        _disclaimerAccepted.value = true
        viewModelScope.launch { AppGraph.settings.acceptDisclaimer() }
    }

    fun runSelfCheck() {
        _containerAvailable.value = AppGraph.container.available
        selfCheckRunner.run()
    }

    /** 首屏进入时自动跑一次，避免用户必须手动点"重新检测"。 */
    fun runSelfCheckIfIdle() {
        if (steps.value.none { it.state != StepState.PENDING }) runSelfCheck()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { StartupViewModel(AppGraph.engineSelfCheck) }
        }
    }
}
