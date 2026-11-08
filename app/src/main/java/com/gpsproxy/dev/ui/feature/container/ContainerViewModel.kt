package com.gpsproxy.dev.ui.feature.container

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.core.device.DeviceCapability
import com.gpsproxy.dev.core.device.DeviceCapabilityChecker
import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.messaging.MessageTone
import com.gpsproxy.dev.core.messaging.Messenger
import com.gpsproxy.dev.core.time.Clock
import com.gpsproxy.dev.data.InstalledApp
import com.gpsproxy.dev.data.TargetAppsRepository
import com.gpsproxy.dev.domain.container.ContainerApp
import com.gpsproxy.dev.domain.container.ContainerImportResult
import com.gpsproxy.dev.domain.container.ContainerRepository
import com.gpsproxy.dev.domain.container.LocationMode
import com.gpsproxy.dev.domain.selfcheck.SelfCheckRunner
import com.gpsproxy.dev.domain.selfcheck.SelfCheckStep
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.simulation.SimulationEngine
import com.gpsproxy.dev.domain.simulation.port.ContainerGateway
import com.gpsproxy.dev.domain.simulation.port.LocationSampleFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 容器相关页面（检测 / 应用列表）的共享 ViewModel。
 *
 * 旧实现的导入、卸载、切换模式、重置散落在 17 处 ViewModel 调用中，
 * 每处都要自己刷新列表与占用统计。这里把"操作 → 收尾刷新"固化成模板，
 * 调用方只需调用一个语义化方法。
 */
class ContainerViewModel(
    application: Application,
    private val containerRepo: ContainerRepository,
    private val containerGateway: ContainerGateway,
    private val targetRepo: TargetAppsRepository,
    private val selfCheck: SelfCheckRunner,
    private val engine: SimulationEngine,
    private val samples: LocationSampleFactory,
    private val clock: Clock,
    private val messenger: Messenger,
) : AndroidViewModel(application) {

    val capability: DeviceCapability = DeviceCapabilityChecker.check()

    val steps: StateFlow<List<SelfCheckStep>> = selfCheck.steps
    val running: StateFlow<Boolean> = selfCheck.running

    /** 自检未通过时的错误码；自检进行中或从未运行时为 null。 */
    val error: StateFlow<ErrorCode?> = combine(selfCheck.running, selfCheck.allPassed) { running, passed ->
        if (running || passed == null || passed) null else ErrorCode.BOX_0101
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _available = MutableStateFlow(containerGateway.available)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _apps = MutableStateFlow<List<ContainerApp>>(emptyList())
    val containerApps: StateFlow<List<ContainerApp>> = _apps.asStateFlow()

    /** 上次读取清单失败（服务不可达）。与"确实没有应用"区分，避免空态误导用户去导入。 */
    private val _appsUnavailable = MutableStateFlow(false)
    val containerAppsUnavailable: StateFlow<Boolean> = _appsUnavailable.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val containerLoading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val containerBusy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _sandboxBytes = MutableStateFlow(0L)
    val sandboxBytes: StateFlow<Long> = _sandboxBytes.asStateFlow()

    private val _hostApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _hostApps.asStateFlow()

    private val _hostAppsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _hostAppsLoading.asStateFlow()

    private var hostAppsLoaded = false

    fun runInit() {
        selfCheck.run()
        _available.value = containerGateway.available
    }

    fun refreshAvailability() {
        _available.value = containerGateway.available
    }

    fun ensureContainerAppsLoaded() {
        if (_loading.value) return
        viewModelScope.launch { refreshApps() }
    }

    /** 导入对话框使用：宿主已安装应用清单。 */
    fun ensureAppsLoaded(force: Boolean = false) {
        if (_hostAppsLoading.value) return
        if (hostAppsLoaded && !force) return
        viewModelScope.launch {
            _hostAppsLoading.value = true
            _hostApps.value = targetRepo.loadLaunchableApps()
            _hostAppsLoading.value = false
            hostAppsLoaded = true
        }
    }

    suspend fun loadAppIcon(packageName: String): Bitmap? = targetRepo.loadIcon(packageName)

    fun importContainerApp(packageName: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            afterImport(containerRepo.importInstalled(packageName))
            _busy.value = false
        }
    }

    /**
     * 从用户选择的 APK 导入。
     *
     * 先把内容 URI 拷到应用缓存目录再安装：容器需要真实文件路径，
     * 且授权只在本次回调内有效，拖到后台线程后可能已失效。
     */
    fun importContainerApk(uri: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(getApplication<Application>().cacheDir, "import-${System.currentTimeMillis()}.apk")
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: return@runCatching ContainerImportResult(
                            false,
                            message = StringsHolder.text { container.unreadableFile },
                        )
                    input.use { source -> temp.outputStream().use { source.copyTo(it) } }
                    val imported = containerRepo.importApk(temp)
                    temp.delete()
                    imported
                }.getOrElse {
                    ContainerImportResult(false, message = StringsHolder.text { container.readFileFailed })
                }
            }
            afterImport(result)
            _busy.value = false
        }
    }

    fun launchContainerApp(packageName: String) {
        if (!containerRepo.launch(packageName)) {
            messenger.show({ container.cannotLaunchInContainer })
        }
    }

    fun uninstallContainerApp(packageName: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val ok = containerRepo.uninstall(packageName)
            messenger.show(
                { if (ok) container.uninstalled else container.uninstallFailed },
                if (ok) MessageTone.SUCCESS else MessageTone.ERROR,
            )
            refreshApps()
            _busy.value = false
        }
    }

    fun setContainerLocationMode(packageName: String, mode: LocationMode) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val ok = containerRepo.setLocationMode(packageName, mode)
            if (ok) {
                _apps.value = _apps.value.map {
                    if (it.packageName == packageName) it.copy(locationMode = mode) else it
                }
                val label = packageName.substringAfterLast('.')
                messenger.show(
                    { container.modeChanged(label, container.locationModeLabel(mode.name)) },
                    MessageTone.SUCCESS,
                )
            } else {
                messenger.show({ container.switchFailed })
            }
            _busy.value = false
        }
    }

    /**
     * 为单个容器应用写入专属坐标（OWN 模式）。
     *
     * 坐标取当前模拟选点；即使模拟未运行也允许设置，用户可以先配好再启动。
     */
    fun setAppLocation(packageName: String) {
        if (_busy.value) return
        val point = engine.state.value.point ?: run {
            messenger.show({ container.pickFirst })
            return
        }
        val settings = AppGraph.currentSettings()
        viewModelScope.launch {
            _busy.value = true
            val sample = samples.sample(
                point = point,
                coordSystem = settings.coordSystem,
                accuracyMeters = settings.accuracyMeters,
                speedMps = 0f,
                bearing = 0f,
                wallMillis = clock.wallMillis(),
            )
            val ok = containerRepo.setAppLocation(packageName, sample)
            messenger.show(
                { if (ok) container.ownSet(packageName) else container.ownSetFailed },
                if (ok) MessageTone.SUCCESS else MessageTone.ERROR,
            )
            refreshApps()
            _busy.value = false
        }
    }

    fun clearAppLocation(packageName: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val ok = containerRepo.clearAppLocation(packageName)
            messenger.show(
                { if (ok) container.ownCleared(packageName) else container.ownClearFailed },
                if (ok) MessageTone.SUCCESS else MessageTone.ERROR,
            )
            refreshApps()
            _busy.value = false
        }
    }

    fun resetContainerLocations() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            containerRepo.resetAllLocations()
            refreshApps()
            _busy.value = false
            messenger.show({ container.restored }, MessageTone.SUCCESS)
        }
    }

    private suspend fun afterImport(result: ContainerImportResult) {
        if (result.success) {
            messenger.show({ container.imported(result.packageName.orEmpty()) }, MessageTone.SUCCESS)
            // 模拟运行中导入的新应用需要立即生效，否则用户会看到"已导入但没生效"
            if (engine.state.value.isActive &&
                AppGraph.currentSettings().injectionChannel == InjectionChannel.CONTAINER
            ) {
                result.packageName?.let { containerRepo.setLocationMode(it, LocationMode.GLOBAL) }
            }
            refreshApps()
        } else {
            // 用错误码的标题做首行，具体原因放在冒号后，避免把系统异常直接抛给用户
            messenger.showError(
                ErrorCode.BOX_0102,
                result.message.ifBlank { StringsHolder.text { container.unknownReason } },
            )
        }
    }

    private suspend fun refreshApps() {
        _loading.value = true
        _available.value = containerGateway.available
        val apps = containerRepo.listApps()
        if (apps == null) {
            // 保留上一次清单；首次读取失败时由界面显示"读取失败"而不是"还没有应用"。
            _appsUnavailable.value = true
        } else {
            _apps.value = apps
            _appsUnavailable.value = false
        }
        _sandboxBytes.value = containerRepo.sandboxSizeBytes()
        _loading.value = false
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ContainerViewModel(
                    application = app,
                    containerRepo = AppGraph.containerApps,
                    containerGateway = AppGraph.container,
                    targetRepo = AppGraph.targetApps,
                    selfCheck = AppGraph.containerSelfCheck,
                    engine = AppGraph.engine,
                    samples = AppGraph.samples,
                    clock = AppGraph.clock,
                    messenger = AppGraph.messenger,
                )
            }
        }
    }
}
