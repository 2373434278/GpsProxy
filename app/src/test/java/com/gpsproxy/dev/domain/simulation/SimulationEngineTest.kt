package com.gpsproxy.dev.domain.simulation

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.core.result.Outcome
import com.gpsproxy.dev.core.result.isSuccess
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.settings.DetailLevel
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.simulation.channel.CellFingerprintPublisher
import com.gpsproxy.dev.domain.simulation.channel.ChannelMonitor
import com.gpsproxy.dev.domain.simulation.channel.ContainerInjectionStrategy
import com.gpsproxy.dev.domain.simulation.channel.InjectionStrategyFactory
import com.gpsproxy.dev.domain.simulation.channel.OfficialMockInjectionStrategy
import com.gpsproxy.dev.domain.simulation.model.ChannelCatalog
import com.gpsproxy.dev.domain.simulation.model.MotionVector
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.domain.simulation.port.LocationSampleFactory
import com.gpsproxy.dev.domain.simulation.route.RouteSourceKind
import com.gpsproxy.dev.domain.simulation.route.SyntheticRouteSource
import com.gpsproxy.dev.testing.FakeClock
import com.gpsproxy.dev.testing.FakeContainerGateway
import com.gpsproxy.dev.testing.FakePermissionFacts
import com.gpsproxy.dev.testing.FakeSettingsGateway
import com.gpsproxy.dev.testing.FakeSystemMockGateway
import com.gpsproxy.dev.testing.SeededRandomSource
import com.gpsproxy.dev.testing.recordingLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SimulationEngineTest {

    private val start = GeoPoint(30.250213, 120.148832)

    private class Harness(
        scope: CoroutineScope,
        val container: FakeContainerGateway,
        val mock: FakeSystemMockGateway,
        val permissions: FakePermissionFacts,
        settings: AppSettings,
        val monitor: ChannelMonitor,
        val engine: SimulationEngine,
        val clock: FakeClock,
    )

    private fun TestScope.harness(
        container: FakeContainerGateway = FakeContainerGateway(),
        mock: FakeSystemMockGateway = FakeSystemMockGateway(),
        permissions: FakePermissionFacts = FakePermissionFacts(),
        settings: AppSettings = AppSettings(),
        recorder: com.gpsproxy.dev.domain.session.SessionRecorder =
            com.gpsproxy.dev.domain.session.SessionRecorder.Noop,
        roamPlanner: com.gpsproxy.dev.domain.simulation.route.RoamPlanner =
            com.gpsproxy.dev.domain.simulation.route.RoamPlanner(SeededRandomSource(7)),
    ): Harness {
        val log = recordingLog()
        val settingsGateway = FakeSettingsGateway(settings)
        val monitor = ChannelMonitor(ChannelCatalog.initialStates(permissions.isMockLocationAllowed()))
        val cells = CellFingerprintPublisher(container, log) { DetailLevel.NORMAL }
        val containerStrategy = ContainerInjectionStrategy(container, monitor, cells, log)
        val clock = FakeClock()
        val officialStrategy =
            OfficialMockInjectionStrategy(mock, container, monitor, permissions, cells, log, clock::elapsedMillis)
        val engine = SimulationEngine(
            settingsGateway = settingsGateway,
            permissions = permissions,
            mockGateway = mock,
            containerGateway = container,
            monitor = monitor,
            strategyFactory = InjectionStrategyFactory(containerStrategy, officialStrategy),
            routeSource = SyntheticRouteSource(),
            samples = LocationSampleFactory(SeededRandomSource()),
            clock = clock,
            log = log,
            scope = backgroundScope,
            sessionRecorder = recorder,
            roamPlanner = roamPlanner,
        )
        return Harness(backgroundScope, container, mock, permissions, settings, monitor, engine, clock)
    }

    @Test
    fun `session recording starts on success and clears on stop`() = runTest {
        val recorder = com.gpsproxy.dev.testing.RecordingSessionRecorder()
        val harness = harness(recorder = recorder)
        harness.engine.initialize(start, "西湖")
        runCurrent()

        harness.engine.startSingle()
        runCurrent()
        assertEquals(1, recorder.startCalls)
        assertEquals(com.gpsproxy.dev.domain.simulation.model.SimMode.SINGLE, recorder.recordedStates.single().mode)

        harness.engine.stop()
        runCurrent()
        assertEquals(1, recorder.stopCalls)
    }

    @Test
    fun `start without a selected point fails with coordinate error`() = runTest {
        val harness = harness()
        runCurrent()
        val result = harness.engine.startSingle()
        assertTrue(result is Outcome.Failure)
        assertEquals(ErrorCode.COORD_0101, (result as Outcome.Failure).error.code)
        assertEquals(RunState.ERROR, harness.engine.state.value.runState)
        assertEquals(SimMode.IDLE, harness.engine.state.value.mode)
    }

    @Test
    fun `container session starts, publishes and restores on stop`() = runTest {
        val harness = harness()
        harness.engine.initialize(start, "西湖")
        runCurrent()

        val result = harness.engine.startSingle()
        assertTrue(result.isSuccess)
        assertEquals(SimMode.SINGLE, harness.engine.state.value.mode)
        assertEquals(RunState.RUNNING, harness.engine.state.value.runState)
        assertEquals(1L, harness.engine.state.value.injectedCount)
        assertEquals(1, harness.container.pushed.size)
        assertEquals(1, harness.container.enableAllCalls)

        harness.engine.stop()
        assertEquals(SimMode.IDLE, harness.engine.state.value.mode)
        assertEquals(RunState.IDLE, harness.engine.state.value.runState)
        assertTrue(harness.container.disableAllCalls >= 1)
        assertTrue(harness.container.clearCellCalls >= 1)
        assertEquals(1, harness.mock.teardownCalls)
    }

    @Test
    fun `container unavailable aborts and reports box error`() = runTest {
        val harness = harness(container = FakeContainerGateway(available = false))
        harness.engine.initialize(start, "西湖")
        runCurrent()

        val result = harness.engine.startSingle()
        assertTrue(result is Outcome.Failure)
        assertEquals(ErrorCode.BOX_0101, (result as Outcome.Failure).error.code)
        assertEquals(SimMode.IDLE, harness.engine.state.value.mode)
        assertEquals(RunState.ERROR, harness.engine.state.value.runState)
        assertTrue(harness.container.pushed.isEmpty())
    }

    @Test
    fun `official channel without mock permission reports perm error`() = runTest {
        val harness = harness(
            mock = FakeSystemMockGateway(denyInstall = true),
            permissions = FakePermissionFacts(mockAllowed = false),
            settings = AppSettings(injectionChannel = InjectionChannel.OFFICIAL),
        )
        harness.engine.initialize(start, "西湖")
        runCurrent()

        val result = harness.engine.startSingle()
        assertTrue(result is Outcome.Failure)
        assertEquals(ErrorCode.PERM_0102, (result as Outcome.Failure).error.code)
    }

    @Test
    fun `pause and resume flip the run state`() = runTest {
        val harness = harness()
        harness.engine.initialize(start, "西湖")
        runCurrent()
        harness.engine.startSingle()

        harness.engine.pause()
        assertEquals(RunState.PAUSED, harness.engine.state.value.runState)
        harness.engine.resume()
        assertEquals(RunState.RUNNING, harness.engine.state.value.runState)
        harness.engine.stop()
    }

    @Test
    fun `route too close is rejected before touching channels`() = runTest {
        val harness = harness()
        runCurrent()
        val config = RouteConfig(start = start, end = start)
        val result = harness.engine.startRoute(config)
        assertTrue(result is Outcome.Failure)
        assertEquals(ErrorCode.ROUTE_0201, (result as Outcome.Failure).error.code)
        assertEquals(0, harness.container.enableAllCalls)
    }

    @Test
    fun `route session plans synthetic path and exposes progress`() = runTest {
        val harness = harness()
        runCurrent()
        val end = GeoUtils.offsetMeters(start, 500.0, 0.0)
        val result = harness.engine.startRoute(RouteConfig(start = start, end = end, speedKmh = 36))
        assertTrue(result.isSuccess)

        val state = harness.engine.state.value
        assertEquals(SimMode.ROUTE, state.mode)
        assertEquals(RunState.RUNNING, state.runState)
        assertEquals(RouteSourceKind.SYNTHETIC, state.routeSource)
        assertNotNull(state.progress)
        val progress = requireNotNull(state.progress)
        assertTrue("total=${progress.distanceTotalM}", progress.distanceTotalM in 450.0..650.0)
        assertTrue(progress.totalPoints >= 24)
        harness.engine.stop()
    }

    @Test
    fun `blur shifts the injected point and is cleared at runtime`() = runTest {
        val harness = harness(settings = AppSettings(blurEnabled = true, blurRadiusMeters = 200))
        harness.engine.initialize(start, "西湖")
        runCurrent()

        val injected = requireNotNull(harness.engine.state.value.injectedPoint)
        assertTrue(GeoUtils.distanceMeters(start, injected) <= 200.0 + 1e-6)
        assertTrue(GeoUtils.distanceMeters(start, injected) > 0.0)
        assertEquals(200, harness.engine.state.value.blurRadiusMeters)

        harness.engine.startSingle()
        harness.engine.setBlurRadius(0)
        assertEquals(start, harness.engine.state.value.injectedPoint)
        assertEquals(0, harness.engine.state.value.blurRadiusMeters)
        harness.engine.stop()
    }

    @Test
    fun `route with waypoints keeps them in state and path`() = runTest {
        val harness = harness()
        val wp1 = GeoUtils.offsetMeters(start, 200.0, 0.0)
        val wp2 = GeoUtils.offsetMeters(wp1, 200.0, 200.0)
        val end = GeoUtils.offsetMeters(wp2, 200.0, 0.0)
        val result = harness.engine.startRoute(
            RouteConfig(
                start = start,
                end = end,
                waypoints = listOf(wp1, wp2, wp1),
                speedKmh = 36,
            ),
        )

        assertTrue(result.isSuccess)
        val state = harness.engine.state.value
        assertEquals(2, state.route?.waypoints?.size)
        assertTrue(state.routePathPoints.size >= 3)
        assertTrue(state.routePathPoints.any { GeoUtils.distanceMeters(it, wp1) < 5.0 })
        assertTrue(state.routePathPoints.any { GeoUtils.distanceMeters(it, wp2) < 5.0 })
        harness.engine.stop()
    }

    @Test
    fun `roam starts a closed loop with roam source`() = runTest {
        val harness = harness()
        val result = harness.engine.startRoute(
            RouteConfig(
                start = start,
                end = start,
                kind = RouteKind.ROAM,
                roamRadiusMeters = 500,
                speedKmh = 5,
            ),
        )

        assertTrue(result.isSuccess)
        val state = harness.engine.state.value
        assertEquals(RouteSourceKind.ROAM, state.routeSource)
        assertEquals(true, state.route?.loop)
        assertEquals(start, state.routePathPoints.first())
        assertEquals(start, state.routePathPoints.last())
        assertTrue(requireNotNull(state.progress).distanceTotalM > 100.0)
        harness.engine.stop()
    }

    @Test
    fun `roam rejects a radius below the minimum`() = runTest {
        val harness = harness()
        val result = harness.engine.startRoute(
            RouteConfig(
                start = start,
                end = start,
                kind = RouteKind.ROAM,
                roamRadiusMeters = 1,
            ),
        )
        assertTrue(result is Outcome.Failure)
        assertEquals(ErrorCode.ROUTE_0201, (result as Outcome.Failure).error.code)
        assertEquals(SimMode.IDLE, harness.engine.state.value.mode)
    }

    @Test
    fun `imported track is used verbatim`() = runTest {
        val harness = harness()
        val points = (0..10).map { GeoUtils.offsetMeters(start, it * 20.0, 0.0) }
        val result = harness.engine.startRoute(
            RouteConfig(
                start = points.first(),
                end = points.last(),
                kind = RouteKind.IMPORTED,
                trackPoints = points,
                speedKmh = 20,
            ),
        )

        assertTrue(result.isSuccess)
        val state = harness.engine.state.value
        assertEquals(RouteSourceKind.IMPORTED, state.routeSource)
        assertEquals(points, state.routePathPoints)
        assertEquals(200.0, requireNotNull(state.progress).distanceTotalM, 1.0)
        harness.engine.stop()
    }

    @Test
    fun `speed seek reverse and restart drive the route runtime`() = runTest {
        val harness = harness()
        val end = GeoUtils.offsetMeters(start, 1_000.0, 0.0)
        assertTrue(
            harness.engine.startRoute(RouteConfig(start = start, end = end, speedKmh = 36)).isSuccess,
        )
        advanceTimeBy(5_000)
        runCurrent()
        val before = requireNotNull(harness.engine.state.value.progress).distanceDoneM
        assertTrue("before=$before", before > 40.0)

        harness.engine.setRouteSpeedFactor(2f)
        assertEquals(2f, harness.engine.state.value.speedFactor, 0.001f)

        harness.engine.seekRouteFraction(0.5f)
        val atHalf = requireNotNull(harness.engine.state.value.progress).distanceDoneM
        assertEquals(0.5f, harness.engine.state.value.progress!!.fraction, 0.01f)

        assertTrue(harness.engine.reverseRoute())
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(harness.engine.state.value.progress!!.distanceDoneM < atHalf)

        // 回到正向并跑到终点，验证完成态与重播。
        assertTrue(harness.engine.reverseRoute())
        harness.engine.seekRouteFraction(0.999f)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(RunState.COMPLETED, harness.engine.state.value.runState)

        assertTrue(harness.engine.restartRoute())
        assertEquals(RunState.RUNNING, harness.engine.state.value.runState)
        harness.engine.stop()
    }

    @Test
    fun `walk moves the single point at the requested speed`() = runTest {
        val harness = harness()
        harness.engine.initialize(start, "西湖")
        runCurrent()
        assertTrue(harness.engine.startSingle().isSuccess)

        harness.engine.setManualMotion(MotionVector(0f, 5f))
        assertTrue(harness.engine.state.value.walking)
        advanceTimeBy(1_000)
        runCurrent()

        val moved = requireNotNull(harness.engine.state.value.point)
        val distance = GeoUtils.distanceMeters(start, moved)
        assertTrue("distance=$distance", distance in 3.0..7.0)
        assertEquals(18.0, harness.engine.state.value.speedKmh, 0.5)

        harness.engine.setManualMotion(null)
        assertFalse(harness.engine.state.value.walking)
        harness.engine.stop()
    }

    @Test
    fun `auto stop ends the session at the deadline`() = runTest {
        val harness = harness(settings = AppSettings(autoStopMinutes = 1))
        harness.engine.initialize(start, "西湖")
        runCurrent()
        assertTrue(harness.engine.startSingle().isSuccess)
        assertTrue(harness.engine.state.value.autoStopAtMillis > 0)

        advanceTimeBy(60_001)
        runCurrent()

        assertEquals(SimMode.IDLE, harness.engine.state.value.mode)
        assertEquals(RunState.IDLE, harness.engine.state.value.runState)
    }
}
