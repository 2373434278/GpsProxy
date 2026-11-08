package com.gpsproxy.dev.domain.simulation.usecase

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.domain.container.ContainerApp
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.settings.DetailLevel
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.simulation.SimulationEngine
import com.gpsproxy.dev.domain.simulation.channel.CellFingerprintPublisher
import com.gpsproxy.dev.domain.simulation.channel.ChannelMonitor
import com.gpsproxy.dev.domain.simulation.channel.ContainerInjectionStrategy
import com.gpsproxy.dev.domain.simulation.channel.InjectionStrategyFactory
import com.gpsproxy.dev.domain.simulation.channel.OfficialMockInjectionStrategy
import com.gpsproxy.dev.domain.simulation.model.ChannelCatalog
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.port.LocationSampleFactory
import com.gpsproxy.dev.domain.simulation.route.SyntheticRouteSource
import com.gpsproxy.dev.testing.FakeClock
import com.gpsproxy.dev.testing.FakeContainerGateway
import com.gpsproxy.dev.testing.FakeContainerRepository
import com.gpsproxy.dev.testing.FakePermissionFacts
import com.gpsproxy.dev.testing.FakeSettingsGateway
import com.gpsproxy.dev.testing.FakeSystemMockGateway
import com.gpsproxy.dev.testing.SeededRandomSource
import com.gpsproxy.dev.testing.recordingLog
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StartSimulationUseCaseTest {

    private val point = GeoPoint(30.250213, 120.148832)

    private fun TestScope.useCase(
        settings: AppSettings = AppSettings(),
        permissions: FakePermissionFacts = FakePermissionFacts(),
        container: FakeContainerGateway = FakeContainerGateway(),
        apps: List<ContainerApp> = listOf(ContainerApp("com.example", "示例")),
        appsUnavailable: Boolean = false,
    ): Pair<StartSimulationUseCase, SimulationEngine> {
        val log = recordingLog()
        val settingsGateway = FakeSettingsGateway(settings)
        val monitor = ChannelMonitor(ChannelCatalog.initialStates(true))
        val cells = CellFingerprintPublisher(container, log) { DetailLevel.NORMAL }
        val engine = SimulationEngine(
            settingsGateway = settingsGateway,
            permissions = permissions,
            mockGateway = FakeSystemMockGateway(),
            containerGateway = container,
            monitor = monitor,
            strategyFactory = InjectionStrategyFactory(
                ContainerInjectionStrategy(container, monitor, cells, log),
                OfficialMockInjectionStrategy(FakeSystemMockGateway(), container, monitor, permissions, cells, log) { 0L },
            ),
            routeSource = SyntheticRouteSource(),
            samples = LocationSampleFactory(SeededRandomSource()),
            clock = FakeClock(),
            log = log,
            scope = backgroundScope,
        )
        val useCase = StartSimulationUseCase(
            engine = engine,
            permissions = permissions,
            container = container,
            containerApps = FakeContainerRepository(apps).apply { listUnavailable = appsUnavailable },
        )
        return useCase to engine
    }

    @Test
    fun `container channel without runtime is blocked before touching the engine`() = runTest {
        val (useCase, engine) = useCase(container = FakeContainerGateway(available = false))
        engine.initialize(point, "西湖")
        runCurrent()

        val result = useCase.startSingle()
        assertTrue(result is StartResult.Blocked)
        result as StartResult.Blocked
        assertEquals(ErrorCode.BOX_0101, result.error.code)
        assertEquals(StartBlockAction.OPEN_CONTAINER_INIT, result.action)
        assertEquals(RunState.IDLE, engine.state.value.runState)
    }

    @Test
    fun `container service hiccup asks for retry instead of import guidance`() = runTest {
        val (useCase, engine) = useCase(appsUnavailable = true)
        engine.initialize(point, "西湖")
        runCurrent()

        val result = useCase.startSingle()
        result as StartResult.Blocked
        assertEquals(ErrorCode.BOX_0101, result.error.code)
        assertEquals(StartBlockAction.RETRY, result.action)
        assertEquals(RunState.IDLE, engine.state.value.runState)
    }

    @Test
    fun `container channel without imported apps is blocked with its own code`() = runTest {
        val (useCase, engine) = useCase(apps = emptyList())
        engine.initialize(point, "西湖")
        runCurrent()

        val result = useCase.startSingle()
        result as StartResult.Blocked
        assertEquals(ErrorCode.BOX_0103, result.error.code)
        assertEquals(StartBlockAction.OPEN_CONTAINER_APPS, result.action)
    }

    @Test
    fun `valid container environment starts the session`() = runTest {
        val (useCase, engine) = useCase()
        engine.initialize(point, "西湖")
        runCurrent()

        val result = useCase.startSingle()
        assertTrue(result is StartResult.Started)
        assertEquals(RunState.RUNNING, engine.state.value.runState)
        engine.stop()
    }

    @Test
    fun `official channel requires mock permission first`() = runTest {
        val (useCase, engine) = useCase(
            settings = AppSettings(injectionChannel = InjectionChannel.OFFICIAL),
            permissions = FakePermissionFacts(mockAllowed = false, locationGranted = true),
        )
        engine.initialize(point, "西湖")
        runCurrent()

        val result = useCase.startSingle()
        result as StartResult.Blocked
        assertEquals(ErrorCode.PERM_0102, result.error.code)
        assertEquals(StartBlockAction.OPEN_DEVELOPER_OPTIONS, result.action)
    }

    @Test
    fun `official channel requires location permission second`() = runTest {
        val (useCase, engine) = useCase(
            settings = AppSettings(injectionChannel = InjectionChannel.OFFICIAL),
            permissions = FakePermissionFacts(mockAllowed = true, locationGranted = false),
        )
        engine.initialize(point, "西湖")
        runCurrent()

        val result = useCase.startSingle()
        result as StartResult.Blocked
        assertEquals(ErrorCode.PERM_0104, result.error.code)
        assertEquals(StartBlockAction.OPEN_PERMISSIONS, result.action)
    }

    @Test
    fun `route failure keeps the domain error`() = runTest {
        val (useCase, engine) = useCase()
        runCurrent()

        val result = useCase.startRoute(RouteConfig(start = point, end = GeoUtils.offsetMeters(point, 5.0, 0.0)))
        result as StartResult.Blocked
        assertEquals(ErrorCode.ROUTE_0201, result.error.code)
        assertEquals(StartBlockAction.NONE, result.action)
    }
}
