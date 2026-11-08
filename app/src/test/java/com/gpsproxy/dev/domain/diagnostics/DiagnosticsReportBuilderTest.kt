package com.gpsproxy.dev.domain.diagnostics

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.log.LogEntry
import com.gpsproxy.dev.core.log.LogSeverity
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.simulation.model.ChannelCatalog
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteProgress
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import com.gpsproxy.dev.domain.simulation.model.TravelMode
import com.gpsproxy.dev.domain.simulation.route.RouteSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportBuilderTest {

    private val selection = GeoPoint(30.25, 120.15)

    private fun snapshot(
        channel: InjectionChannel = InjectionChannel.CONTAINER,
    ) = DiagnosticsSnapshot(
        generatedAtMillis = 1_700_000_000_000L,
        appVersion = "0.1.0",
        buildType = "debug",
        manufacturer = "TestCorp",
        model = "TestPhone",
        apiLevel = 34,
        supportedAbis = listOf("arm64-v8a"),
        settings = AppSettings(injectionChannel = channel),
        simulation = SimulationState(
            mode = SimMode.ROUTE,
            runState = RunState.RUNNING,
            point = selection,
            injectedPoint = selection,
            blurRadiusMeters = 0,
            route = RouteConfig(selection, selection, TravelMode.DRIVING),
            routeSource = RouteSourceKind.NETWORK,
            progress = RouteProgress(fraction = 0.5f, distanceDoneM = 50.0, distanceTotalM = 100.0),
        ),
        channels = ChannelCatalog.initialStates(true),
        containerAvailable = true,
        containerAppCount = 3,
        mockLocationAllowed = true,
        logs = listOf(LogEntry(0L, LogSeverity.INFO, "hello log")),
    )

    @Test
    fun `report contains all expected sections`() {
        val report = DiagnosticsReportBuilder().build(snapshot())
        assertEquals(
            setOf(
                DiagnosticsReportBuilder.FILE_DEVICE,
                DiagnosticsReportBuilder.FILE_SETTINGS,
                DiagnosticsReportBuilder.FILE_CHANNELS,
                DiagnosticsReportBuilder.FILE_SESSION,
                DiagnosticsReportBuilder.FILE_LOGS,
            ),
            report.sections.keys,
        )
    }

    @Test
    fun `channel capability boundary is stated honestly`() {
        val container = DiagnosticsReportBuilder().build(snapshot(InjectionChannel.CONTAINER)).sections
        assertTrue(container.getValue("device.txt").contains("is_mock_flag=false"))
        val official = DiagnosticsReportBuilder().build(snapshot(InjectionChannel.OFFICIAL)).sections
        assertTrue(official.getValue("device.txt").contains("is_mock_flag=true"))
    }

    @Test
    fun `session section includes route metadata and logs are included`() {
        val sections = DiagnosticsReportBuilder().build(snapshot()).sections
        val session = sections.getValue("session.txt")
        assertTrue(session.contains("mode=ROUTE"))
        assertTrue(session.contains("route_source=NETWORK"))
        assertTrue(session.contains("route_done_m=50.0"))
        assertTrue(sections.getValue("logs.txt").contains("hello log"))
    }

    @Test
    fun `report never leaks package names or api keys`() {
        val text = DiagnosticsReportBuilder().build(snapshot()).sections.values.joinToString("\n")
        assertFalse(text.contains("com.example"))
        assertFalse(text.contains("secret"))
    }
}
