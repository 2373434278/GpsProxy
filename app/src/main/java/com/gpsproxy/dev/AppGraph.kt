package com.gpsproxy.dev

import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import com.gpsproxy.dev.container.ContainerAppsRepository
import com.gpsproxy.dev.core.i18n.I18n
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.core.log.LogBus
import com.gpsproxy.dev.core.messaging.AppMessenger
import com.gpsproxy.dev.core.messaging.MessageTone
import com.gpsproxy.dev.core.messaging.Messenger
import com.gpsproxy.dev.core.random.DefaultRandomSource
import com.gpsproxy.dev.core.time.Clock
import com.gpsproxy.dev.data.PlacesRepository
import com.gpsproxy.dev.data.SettingsRepository
import com.gpsproxy.dev.data.TargetAppsRepository
import com.gpsproxy.dev.data.net.UrlConnectionHttpTextClient
import com.gpsproxy.dev.data.route.AmapRouteSource
import com.gpsproxy.dev.data.route.RoutesRepository
import com.gpsproxy.dev.data.session.DataStoreSessionRecorder
import com.gpsproxy.dev.data.session.SessionStore
import com.gpsproxy.dev.data.poi.AmapPoiSource
import com.gpsproxy.dev.data.poi.CachingPoiSource
import com.gpsproxy.dev.data.poi.FallbackPoiSource
import com.gpsproxy.dev.data.poi.LocalPoiSource
import com.gpsproxy.dev.domain.diagnostics.DiagnosticsReportBuilder
import com.gpsproxy.dev.domain.place.PoiSearchSource
import com.gpsproxy.dev.domain.place.usecase.PlaceUseCases
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.simulation.SimulationEngine
import com.gpsproxy.dev.domain.simulation.channel.CellFingerprintPublisher
import com.gpsproxy.dev.domain.simulation.channel.ChannelMonitor
import com.gpsproxy.dev.domain.simulation.channel.ContainerInjectionStrategy
import com.gpsproxy.dev.domain.simulation.channel.InjectionStrategyFactory
import com.gpsproxy.dev.domain.simulation.channel.OfficialMockInjectionStrategy
import com.gpsproxy.dev.domain.simulation.model.ChannelCatalog
import com.gpsproxy.dev.domain.simulation.port.ContainerGateway
import com.gpsproxy.dev.domain.simulation.port.LocationSampleFactory
import com.gpsproxy.dev.domain.simulation.port.PermissionFacts
import com.gpsproxy.dev.domain.simulation.port.SystemMockGateway
import com.gpsproxy.dev.domain.simulation.route.FallbackRouteSource
import com.gpsproxy.dev.domain.simulation.route.RoamPlanner
import com.gpsproxy.dev.domain.simulation.route.RouteSource
import com.gpsproxy.dev.domain.simulation.route.SyntheticRouteSource
import com.gpsproxy.dev.domain.simulation.usecase.StartSimulationUseCase
import com.gpsproxy.dev.domain.selfcheck.SelfCheckRunner
import com.gpsproxy.dev.domain.track.GpxCodec
import com.gpsproxy.dev.domain.track.usecase.ExportTrackUseCase
import com.gpsproxy.dev.domain.track.usecase.ImportTrackUseCase
import com.gpsproxy.dev.platform.container.BlackBoxContainerGateway
import com.gpsproxy.dev.platform.device.AndroidPermissionFacts
import com.gpsproxy.dev.platform.diagnostics.DiagnosticsExporter
import com.gpsproxy.dev.platform.file.ContentResolverTrackFileGateway
import com.gpsproxy.dev.platform.location.AndroidSystemMockGateway
import com.gpsproxy.dev.platform.map.ExternalMapLauncher
import com.gpsproxy.dev.platform.selfcheck.AndroidSelfCheckFactory
import com.gpsproxy.dev.platform.service.SimulationSessionController
import com.gpsproxy.dev.platform.time.AndroidClock
import com.gpsproxy.dev.ui.session.PlaceSelectionStore
import com.gpsproxy.dev.ui.session.RouteDraftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 组合根（Composition Root）。
 *
 * 整个应用只有这里允许 `new` 具体实现，其余代码一律依赖接口/端口，
 * 由构造函数注入。这样做的原因：
 * - 领域层可在纯 JVM 单测里用 Fake 装配，不需要 Android 运行时；
 * - 替换容器引擎、路网来源、时钟等只需改这一处；
 * - 避免引入 Hilt/Dagger：本工程 Application 需要在 `attachBaseContext`
 *   阶段被 BlackBox 二次接管，注解处理器的自动注入时机与其冲突风险较高，
 *   而所需依赖数量（个位数）远未到需要框架的程度。
 *
 * 初始化是幂等的，且只在宿主主进程调用（见 `GpsProxyApp`）。
 */
@SuppressLint("StaticFieldLeak") // 仅持有 applicationContext，生命周期与进程一致
object AppGraph {

    private val lock = Any()

    @Volatile
    private var initialized = false

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsRepository
        private set
    lateinit var places: PlacesRepository
        private set
    lateinit var routes: RoutesRepository
        private set
    lateinit var poi: PoiSearchSource
        private set
    lateinit var targetApps: TargetAppsRepository
        private set
    lateinit var containerApps: ContainerAppsRepository
        private set

    /** GPX 轨迹文件读写与用例。 */
    lateinit var trackFiles: ContentResolverTrackFileGateway
        private set
    lateinit var importTrack: ImportTrackUseCase
        private set
    lateinit var exportTrack: ExportTrackUseCase
        private set

    /** 外部地图跳转。 */
    lateinit var externalMap: ExternalMapLauncher
        private set

    /** 供跨页面共享的瞬时提示通道；UI 只收集一次。 */
    lateinit var messenger: Messenger
        private set

    /** 界面语言运行时；由设置流驱动，Compose 通过 `LocalStrings` 读取。 */
    lateinit var i18n: I18n
        private set

    lateinit var clock: Clock
        private set

    lateinit var container: ContainerGateway
        private set

    lateinit var engine: SimulationEngine
        private set

    /** 会话恢复信息（开机提醒）。 */
    lateinit var sessionStore: SessionStore
        private set

    /** 跨屏选点草稿与巡航草稿。 */
    lateinit var selection: PlaceSelectionStore
        private set
    lateinit var routeDraft: RouteDraftStore
        private set

    /** 前台服务 / 悬浮窗生命周期控制。 */
    lateinit var session: SimulationSessionController
        private set

    /** 应用服务（用例）。 */
    lateinit var startSimulation: StartSimulationUseCase
        private set
    lateinit var placeUseCases: PlaceUseCases
        private set
    lateinit var searchUseCase: PlaceUseCases.Search
        private set

    /** 启动自检与容器专项自检（结果跨页面共享）。 */
    lateinit var engineSelfCheck: SelfCheckRunner
        private set
    lateinit var containerSelfCheck: SelfCheckRunner
        private set

    /** 定位载荷工厂：容器专属坐标等功能需要与引擎一致的精度/海拔推导。 */
    lateinit var samples: LocationSampleFactory
        private set

    /** 诊断报告组装与导出。 */
    lateinit var diagnosticsBuilder: DiagnosticsReportBuilder
        private set
    lateinit var diagnosticsExporter: DiagnosticsExporter
        private set

    /** 非热路径使用的设置快照，由 DataStore 流持续更新。 */
    @Volatile
    private var settingsSnapshot = AppSettings()

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val app = context.applicationContext

            clock = AndroidClock()
            settings = SettingsRepository(app)
            i18n = I18n()
            StringsHolder.install { i18n.current }
            messenger = AppMessenger { i18n.current }
            places = PlacesRepository(app)
            routes = RoutesRepository(app)
            sessionStore = SessionStore(app)
            trackFiles = ContentResolverTrackFileGateway(app)
            importTrack = ImportTrackUseCase(trackFiles, GpxCodec)
            exportTrack = ExportTrackUseCase(trackFiles, GpxCodec)
            externalMap = ExternalMapLauncher(app)
            val httpClient = UrlConnectionHttpTextClient()
            val amapKey = BuildConfig.AMAP_WEB_KEY.trim()
            poi = createPoiSource(httpClient, amapKey)
            targetApps = TargetAppsRepository(app)
            containerApps = ContainerAppsRepository(app)

            selection = PlaceSelectionStore()
            routeDraft = RouteDraftStore()
            session = SimulationSessionController(app)

            container = BlackBoxContainerGateway()
            val permissions: PermissionFacts = AndroidPermissionFacts(app)
            val mockGateway: SystemMockGateway = AndroidSystemMockGateway(app)
            val monitor = ChannelMonitor(
                ChannelCatalog.initialStates(permissions.isMockLocationAllowed()),
            )
            val cells = CellFingerprintPublisher(
                container = container,
                log = LogBus,
                detailLevel = { settingsSnapshot.detailLevel },
            )
            val containerStrategy = ContainerInjectionStrategy(
                container = container,
                monitor = monitor,
                cells = cells,
                log = LogBus,
            )
            val officialStrategy = OfficialMockInjectionStrategy(
                mock = mockGateway,
                container = container,
                monitor = monitor,
                permissions = permissions,
                cells = cells,
                log = LogBus,
                elapsedMillis = clock::elapsedMillis,
            )
            val strategyFactory = InjectionStrategyFactory(containerStrategy, officialStrategy)
            val random = DefaultRandomSource()
            // 配置了高德 Key 才尝试真实路网；失败/无 Key 时回退内置曲线，界面会标注来源。
            val routeSource: RouteSource = if (amapKey.isNotEmpty()) {
                FallbackRouteSource(
                    primary = AmapRouteSource(httpClient, amapKey),
                    fallback = SyntheticRouteSource(),
                )
            } else {
                SyntheticRouteSource()
            }

            engine = SimulationEngine(
                settingsGateway = settings,
                permissions = permissions,
                mockGateway = mockGateway,
                containerGateway = container,
                monitor = monitor,
                strategyFactory = strategyFactory,
                routeSource = routeSource,
                samples = LocationSampleFactory(random).also { samples = it },
                clock = clock,
                log = LogBus,
                scope = appScope,
                sessionRecorder = DataStoreSessionRecorder(sessionStore),
                roamPlanner = RoamPlanner(random),
                routeUsageRecorder = routes,
                onAutoStopFinished = {
                    session.stopInjectionService()
                    session.stopOverlay()
                    messenger.show({ message.autoStopped }, MessageTone.INFO)
                },
                strings = { i18n.current },
            )
            startSimulation = StartSimulationUseCase(
                engine = engine,
                permissions = permissions,
                container = container,
                containerApps = containerApps,
            )
            placeUseCases = PlaceUseCases(engine = engine, places = places)
            searchUseCase = PlaceUseCases.Search(source = poi, places = places)

            val selfCheckFactory = AndroidSelfCheckFactory(app, container) { i18n.current }
            diagnosticsBuilder = DiagnosticsReportBuilder()
            diagnosticsExporter = DiagnosticsExporter(app)
            engineSelfCheck = SelfCheckRunner(
                scope = appScope,
                definitions = { selfCheckFactory.engineChecks() },
                onFinished = { passed ->
                    appScope.launch {
                        settings.markSelfCheckCompleted(clock.wallMillis())
                        LogBus.info("engine self-check finished · passed=$passed")
                    }
                },
            )
            containerSelfCheck = SelfCheckRunner(
                scope = appScope,
                definitions = { selfCheckFactory.containerChecks() },
                onFinished = { passed -> LogBus.info("container self-check finished · passed=$passed") },
            )

            appScope.launch {
                settings.settings.collect {
                    settingsSnapshot = it
                    i18n.apply(it.language)
                }
            }
            app.registerComponentCallbacks(
                object : ComponentCallbacks {
                    override fun onConfigurationChanged(newConfig: Configuration) {
                        i18n.refreshSystemLocale()
                    }

                    override fun onLowMemory() = Unit
                },
            )
            initialized = true
        }
    }

    /**
     * 组装 POI 数据源链：缓存 → 高德（有 Key 时）→ 内置示例。
     *
     * 顺序是有意的：缓存避免重复请求；高德只在配置了 Web 服务 Key 时参与；
     * 内置示例保证任何环境下都有可用数据。
     */
    private fun createPoiSource(client: UrlConnectionHttpTextClient, amapKey: String): PoiSearchSource {
        val live = if (amapKey.isNotEmpty()) {
            AmapPoiSource(client, amapKey)
        } else {
            null
        }
        return CachingPoiSource(
            delegate = FallbackPoiSource(primary = live, fallback = LocalPoiSource()),
            clock = clock,
        )
    }

    /**
     * 读取一次当前设置快照。
     *
     * 仅用于非热路径的同步决策（如自检、通知文案）；热路径请使用
     * `SettingsGateway.settings` 收集后的快照。
     */
    fun currentSettings(): AppSettings = settingsSnapshot
}
