package com.gpsproxy.dev.domain.simulation.channel

import com.gpsproxy.dev.domain.simulation.model.ChannelCatalog
import com.gpsproxy.dev.domain.simulation.model.ChannelId
import com.gpsproxy.dev.domain.simulation.model.ChannelNoteKind
import com.gpsproxy.dev.domain.simulation.model.ChannelStatus
import com.gpsproxy.dev.domain.simulation.port.InstallReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMonitorTest {

    private fun monitor() = ChannelMonitor(ChannelCatalog.initialStates(mockAllowed = true))

    private fun ChannelMonitor.statusOf(id: ChannelId): ChannelStatus =
        states.value.first { it.id == id }.status

    private fun ChannelMonitor.countOf(id: ChannelId): Long =
        states.value.first { it.id == id }.count

    @Test
    fun `initial catalog exposes every channel with a note`() {
        val states = monitor().states.value
        assertEquals(ChannelId.entries.size, states.size)
        assertTrue(states.all { it.note != null })
    }

    @Test
    fun `container marks reflect readiness`() {
        val monitor = monitor()
        monitor.markContainerReady(3)
        assertEquals(ChannelStatus.OK, monitor.statusOf(ChannelId.CONTAINER))
        assertEquals(
            ChannelNoteKind.CONTAINER_READY,
            monitor.states.value.first { it.id == ChannelId.CONTAINER }.note?.kind,
        )
        monitor.markContainerEmpty()
        assertEquals(ChannelStatus.WARN, monitor.statusOf(ChannelId.CONTAINER))
        monitor.markContainerUnavailable()
        assertEquals(ChannelStatus.BAD, monitor.statusOf(ChannelId.CONTAINER))
    }

    @Test
    fun `install report updates official channels individually`() {
        val monitor = monitor()
        monitor.onInstalled(
            InstallReport(
                installed = listOf(ChannelId.GPS),
                providerNames = listOf("gps"),
            )
        )
        assertEquals(ChannelStatus.OK, monitor.statusOf(ChannelId.GPS))
        assertEquals(ChannelStatus.BAD, monitor.statusOf(ChannelId.NETWORK))
        assertEquals(ChannelStatus.WARN, monitor.statusOf(ChannelId.FUSED))
    }

    @Test
    fun `hits accumulate per channel`() {
        val monitor = monitor()
        monitor.recordHits(listOf(ChannelId.GPS, ChannelId.NETWORK))
        monitor.recordHits(listOf(ChannelId.GPS))
        assertEquals(2L, monitor.countOf(ChannelId.GPS))
        assertEquals(1L, monitor.countOf(ChannelId.NETWORK))
        assertEquals(0L, monitor.countOf(ChannelId.CELL))
        monitor.recordHit(ChannelId.CELL)
        assertEquals(1L, monitor.countOf(ChannelId.CELL))
        assertEquals(ChannelStatus.OK, monitor.statusOf(ChannelId.CELL))
    }

    @Test
    fun `environment refresh does not override a running session`() {
        val monitor = monitor()
        monitor.markContainerReady(1)
        monitor.refreshEnvironment(mockAllowed = false, containerAvailable = false, active = true)
        assertEquals(ChannelStatus.OK, monitor.statusOf(ChannelId.CONTAINER))
    }

    @Test
    fun `environment refresh updates idle notes`() {
        val monitor = monitor()
        monitor.refreshEnvironment(mockAllowed = false, containerAvailable = false, active = false)
        assertEquals(ChannelStatus.BAD, monitor.statusOf(ChannelId.CONTAINER))
        assertEquals(
            ChannelNoteKind.NOT_MOCK_APP,
            monitor.states.value.first { it.id == ChannelId.GPS }.note?.kind,
        )
    }

    @Test
    fun `stop resets runtime channels`() {
        val monitor = monitor()
        monitor.recordHits(listOf(ChannelId.GPS))
        monitor.resetToStopped()
        assertEquals(ChannelStatus.OFF, monitor.statusOf(ChannelId.GPS))
        assertEquals(ChannelStatus.WARN, monitor.statusOf(ChannelId.FUSED))
        assertEquals(ChannelStatus.OFF, monitor.statusOf(ChannelId.CONTAINER))
    }
}
