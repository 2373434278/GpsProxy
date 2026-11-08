package com.gpsproxy.dev.domain.simulation.channel

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.domain.settings.DetailLevel
import com.gpsproxy.dev.domain.simulation.model.ChannelCatalog
import com.gpsproxy.dev.domain.simulation.model.ChannelId
import com.gpsproxy.dev.domain.simulation.model.ChannelStatus
import com.gpsproxy.dev.domain.simulation.port.LocationSample
import com.gpsproxy.dev.testing.FakeContainerGateway
import com.gpsproxy.dev.testing.FakePermissionFacts
import com.gpsproxy.dev.testing.FakeSystemMockGateway
import com.gpsproxy.dev.testing.recordingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectionStrategyTest {

    private val point = GeoPoint(30.250213, 120.148832)
    private val sample = LocationSample(
        point = point,
        coordSystem = CoordSystem.GCJ02,
        accuracyMeters = 5,
        altitude = 12.0,
        speedMps = 0f,
        bearing = 0f,
        satellites = 12,
        wallMillis = 1L,
    )

    private fun containerStrategy(
        container: FakeContainerGateway = FakeContainerGateway(),
        monitor: ChannelMonitor = ChannelMonitor(ChannelCatalog.initialStates(true)),
    ): Triple<ContainerInjectionStrategy, ChannelMonitor, FakeContainerGateway> {
        val cells = CellFingerprintPublisher(container, recordingLog()) { DetailLevel.NORMAL }
        return Triple(
            ContainerInjectionStrategy(container, monitor, cells, recordingLog()),
            monitor,
            container,
        )
    }

    private fun officialStrategy(
        mock: FakeSystemMockGateway = FakeSystemMockGateway(),
        container: FakeContainerGateway = FakeContainerGateway(),
        permissions: FakePermissionFacts = FakePermissionFacts(),
        monitor: ChannelMonitor = ChannelMonitor(ChannelCatalog.initialStates(true)),
        elapsedMillis: () -> Long = { 0L },
    ): OfficialMockInjectionStrategy {
        val cells = CellFingerprintPublisher(container, recordingLog()) { DetailLevel.NORMAL }
        return OfficialMockInjectionStrategy(
            mock,
            container,
            monitor,
            permissions,
            cells,
            recordingLog(),
            elapsedMillis,
        )
    }

    @Test
    fun `container prepare fails without runtime and never falls back`() {
        val container = FakeContainerGateway(available = false)
        val (strategy, monitor, _) = containerStrategy(container)
        val readiness = strategy.prepare()
        assertFalse(readiness.ready)
        assertEquals(ErrorCode.BOX_0101, readiness.error)
        assertEquals(ChannelStatus.BAD, monitor.states.value.first { it.id == ChannelId.CONTAINER }.status)
        assertEquals(0, container.pushed.size)
    }

    @Test
    fun `container prepare fails when no apps are imported`() {
        val container = FakeContainerGateway(enabledCount = 0)
        val (strategy, monitor, _) = containerStrategy(container)
        assertFalse(strategy.prepare().ready)
        assertEquals(ChannelStatus.WARN, monitor.states.value.first { it.id == ChannelId.CONTAINER }.status)
    }

    @Test
    fun `container publish reports coordinate and cell hits`() {
        val (strategy, _, container) = containerStrategy()
        val first = strategy.publish(sample)
        assertTrue(first.success)
        assertEquals(listOf(ChannelId.CONTAINER, ChannelId.CELL), first.hits)
        assertEquals(1, first.injectedDelta)
        assertEquals(1, container.pushed.size)
        assertEquals(1, container.cells.size)

        val second = strategy.publish(sample)
        assertEquals(listOf(ChannelId.CONTAINER), second.hits)
        assertEquals(1, container.cells.size)
    }

    @Test
    fun `official prepare maps denied install to permission error`() {
        val mock = FakeSystemMockGateway(denyInstall = true)
        val permissions = FakePermissionFacts(mockAllowed = false)
        val strategy = officialStrategy(mock = mock, permissions = permissions)
        val readiness = strategy.prepare()
        assertFalse(readiness.ready)
        assertEquals(ErrorCode.PERM_0102, strategy.failureError)
        assertEquals(ErrorCode.PERM_0102, readiness.error)
    }

    @Test
    fun `official publish mirrors into container when available`() {
        val mock = FakeSystemMockGateway()
        val container = FakeContainerGateway(available = true)
        val strategy = officialStrategy(mock = mock, container = container)
        val report = strategy.publish(sample)
        assertTrue(report.success)
        assertEquals(listOf(ChannelId.GPS, ChannelId.NETWORK, ChannelId.CONTAINER, ChannelId.CELL), report.hits)
        assertEquals(3, report.injectedDelta)
        assertEquals(listOf("gps", "network"), report.providerNames)
        assertEquals(1, container.pushed.size)
    }

    @Test
    fun `official mirror stops hammering the container after a failed push`() {
        val mock = FakeSystemMockGateway()
        val container = FakeContainerGateway(available = true, pushSucceeds = false)
        var now = 0L
        val strategy = officialStrategy(
            mock = mock,
            container = container,
            elapsedMillis = { now },
        )

        val first = strategy.publish(sample)
        // 系统 Mock 主通道仍然成功，只是不再声称向容器下发过。
        assertTrue(first.success)
        assertEquals(listOf(ChannelId.GPS, ChannelId.NETWORK), first.hits)
        assertEquals(1, container.pushAttempts)

        strategy.publish(sample)
        assertEquals(1, container.pushAttempts)

        now += 10_000
        strategy.publish(sample)
        assertEquals(2, container.pushAttempts)
    }

    @Test
    fun `release cleans up both channel and cell state`() {
        val (strategy, _, container) = containerStrategy()
        strategy.release()
        assertEquals(1, container.disableAllCalls)
        assertEquals(1, container.clearCellCalls)
    }
}
