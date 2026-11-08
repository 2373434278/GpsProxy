package com.gpsproxy.dev.testing

import com.gpsproxy.dev.core.log.InMemoryLogSink
import com.gpsproxy.dev.core.random.RandomSource
import com.gpsproxy.dev.core.time.Clock
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.settings.SettingsGateway
import com.gpsproxy.dev.domain.simulation.cell.CellSpec
import com.gpsproxy.dev.domain.simulation.model.ChannelId
import com.gpsproxy.dev.domain.simulation.port.ContainerGateway
import com.gpsproxy.dev.domain.simulation.port.InstallReport
import com.gpsproxy.dev.domain.simulation.port.LocationSample
import com.gpsproxy.dev.domain.simulation.port.PermissionFacts
import com.gpsproxy.dev.domain.simulation.port.PushReport
import com.gpsproxy.dev.domain.simulation.port.SystemMockGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 测试替身集合。
 *
 * 这些类只存在于单测源码集里：领域层通过端口注入，测试无需 Android 运行时。
 * 有意保留"行为记录"（计数、最后参数）而不是只返回布尔值，
 * 这样断言可以覆盖"是否真的调用了、调用了几次、参数对不对"。
 */

/** 可手动推进的假时钟；[delay] 走测试调度器，并同步推进读数。 */
class FakeClock(var elapsed: Long = 0L, var wall: Long = 1_700_000_000_000L) : Clock {
    override fun elapsedMillis(): Long = elapsed
    override fun wallMillis(): Long = wall
    override suspend fun delay(millis: Long) {
        kotlinx.coroutines.delay(millis)
        // 与真实时钟语义一致：睡眠期间读数前进，帧泵才能算出正确的时间差。
        elapsed += millis
        wall += millis
    }
}

/** 固定种子的随机源，保证同一测试多次运行结果一致。 */
class SeededRandomSource(seed: Long = 42L) : RandomSource {
    private val random = kotlin.random.Random(seed)
    override fun nextInt(from: Int, until: Int): Int =
        if (until <= from) from else from + random.nextInt(until - from)

    override fun nextDouble(): Double = random.nextDouble()
}

/** 内存设置仓库：可手动推送新快照并记录 rememberPoint 调用。 */
class FakeSettingsGateway(initial: AppSettings = AppSettings()) : SettingsGateway {

    private val state = MutableStateFlow(initial)
    val rememberedPoints = mutableListOf<Triple<Double, Double, String>>()

    override val settings: Flow<AppSettings> = state

    fun update(value: AppSettings) {
        state.value = value
    }

    override suspend fun rememberPoint(lat: Double, lng: Double, address: String) {
        rememberedPoints += Triple(lat, lng, address)
    }
}

/** 记录调用的容器网关。 */
class FakeContainerGateway(
    override var available: Boolean = true,
    var enabledCount: Int = 1,
    var pushSucceeds: Boolean = true,
    var cellPushSucceeds: Boolean = true,
) : ContainerGateway {

    val pushed = mutableListOf<LocationSample>()
    val cells = mutableListOf<CellSpec>()
    val appLocations = mutableListOf<Pair<String, LocationSample>>()
    var enableAllCalls = 0
    var disableAllCalls = 0
    var clearCellCalls = 0
    var probeCalls = 0
    var pushAttempts = 0

    override fun enableAll(): Int {
        enableAllCalls++
        return enabledCount
    }

    override fun disableAll() {
        disableAllCalls++
    }

    override fun push(sample: LocationSample): Boolean {
        pushAttempts++
        if (!pushSucceeds) return false
        pushed += sample
        return true
    }

    override fun pushCell(cell: CellSpec): Boolean {
        if (!cellPushSucceeds) return false
        cells += cell
        return true
    }

    override fun clearCell(): Boolean {
        clearCellCalls++
        return true
    }

    override fun probe(): Boolean {
        probeCalls++
        return available
    }

    override fun setAppLocation(packageName: String, sample: LocationSample): Boolean {
        if (!pushSucceeds) return false
        appLocations += packageName to sample
        return true
    }
}

/** 记录调用的系统 Mock 网关。 */
class FakeSystemMockGateway(
    var installed: Boolean = true,
    var pushSucceeds: Boolean = true,
    var denyInstall: Boolean = false,
) : SystemMockGateway {

    var installCalls = 0
    var teardownCalls = 0
    var pushCalls = 0

    override val installedProviderNames: List<String>
        get() = if (installed) listOf("gps", "network") else emptyList()

    override fun install(): InstallReport {
        installCalls++
        if (denyInstall) {
            installed = false
            return InstallReport(failures = listOf("gps: 未授权写入模拟位置"))
        }
        installed = true
        return InstallReport(
            installed = listOf(ChannelId.GPS, ChannelId.NETWORK),
            providerNames = listOf("gps", "network"),
        )
    }

    override fun push(sample: LocationSample): PushReport {
        pushCalls++
        if (!pushSucceeds) {
            return PushReport(failures = listOf("gps: boom"))
        }
        return PushReport(
            hits = listOf(ChannelId.GPS, ChannelId.NETWORK),
            providerNames = listOf("gps", "network"),
        )
    }

    override fun teardown() {
        teardownCalls++
        installed = false
    }
}

/** 权限事实替身。 */
class FakePermissionFacts(
    var mockAllowed: Boolean = true,
    var locationGranted: Boolean = true,
) : PermissionFacts {
    override fun isMockLocationAllowed(): Boolean = mockAllowed
    override fun locationGranted(): Boolean = locationGranted
}

/** 纯内存日志，便于断言"是否记录了某条日志"。 */
fun recordingLog(): InMemoryLogSink = InMemoryLogSink(timestamp = { 0L })

/** 容器应用仓库替身：默认没有应用，可注入任意清单。 */
class FakeContainerRepository(
    var apps: List<com.gpsproxy.dev.domain.container.ContainerApp> = emptyList(),
) : com.gpsproxy.dev.domain.container.ContainerRepository {

    var importResult = com.gpsproxy.dev.domain.container.ContainerImportResult(success = true)
    var importedPackages = mutableListOf<String>()
    var ownLocations = mutableListOf<String>()

    /** 模拟容器服务短暂不可达：listApps 返回 null 而不是空清单。 */
    var listUnavailable = false

    override suspend fun listApps(): List<com.gpsproxy.dev.domain.container.ContainerApp>? =
        if (listUnavailable) null else apps

    override suspend fun setLocationMode(
        packageName: String,
        mode: com.gpsproxy.dev.domain.container.LocationMode,
    ): Boolean = true

    override suspend fun importInstalled(
        packageName: String,
    ): com.gpsproxy.dev.domain.container.ContainerImportResult {
        importedPackages += packageName
        return importResult
    }

    override suspend fun importApk(
        apk: java.io.File,
    ): com.gpsproxy.dev.domain.container.ContainerImportResult = importResult

    override fun launch(packageName: String): Boolean = true

    override suspend fun uninstall(packageName: String): Boolean = true

    override suspend fun sandboxSizeBytes(): Long = 0L

    override suspend fun resetAllLocations(): Int = apps.size

    override suspend fun setAppLocation(
        packageName: String,
        sample: com.gpsproxy.dev.domain.simulation.port.LocationSample,
    ): Boolean {
        ownLocations += packageName
        return true
    }

    override suspend fun clearAppLocation(packageName: String): Boolean = true
}

/** 可编排响应的 HTTP 替身，用于 POI / 路径规划解析测试。 */
class FakeHttpTextClient(
    private val responder: (String, Map<String, String>) -> String,
) : com.gpsproxy.dev.data.net.HttpTextClient {
    var calls = 0
    var lastUrl: String = ""
    var lastParams: Map<String, String> = emptyMap()

    override suspend fun get(baseUrl: String, params: Map<String, String>): String {
        calls++
        lastUrl = baseUrl
        lastParams = params
        return responder(baseUrl, params)
    }
}

/** 记录会话录制调用，用于验证"启动成功才记录、停止即清理"。 */
class RecordingSessionRecorder : com.gpsproxy.dev.domain.session.SessionRecorder {
    var startCalls = 0
    var stopCalls = 0
    val recordedStates =
        mutableListOf<com.gpsproxy.dev.domain.simulation.model.SimulationState>()

    override suspend fun onStarted(state: com.gpsproxy.dev.domain.simulation.model.SimulationState) {
        startCalls++
        recordedStates += state
    }

    override suspend fun onStopped() {
        stopCalls++
    }
}
