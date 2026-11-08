package com.gpsproxy.dev.domain.simulation.state

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteProgress
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import com.gpsproxy.dev.domain.simulation.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationStateMachineTest {

    private val machine = SimulationStateMachine()
    private val point = GeoPoint(30.25, 120.15)

    private fun singlePayload() = SingleStartPayload(
        target = point,
        injected = point,
        blurRadiusMeters = 0,
        accuracyMeters = 5,
        installedProviders = emptyList(),
        startedAt = 100L,
        channel = com.gpsproxy.dev.domain.settings.InjectionChannel.CONTAINER,
    )

    private fun routePayload() = RouteStartPayload(
        config = RouteConfig(start = point, end = GeoPoint(30.26, 120.16), mode = TravelMode.DRIVING),
        target = point,
        injected = point,
        blurRadiusMeters = 0,
        accuracyMeters = 5,
        bearing = 0f,
        progress = RouteProgress(distanceTotalM = 100.0),
        installedProviders = emptyList(),
        startedAt = 100L,
        routeSource = com.gpsproxy.dev.domain.simulation.route.RouteSourceKind.SYNTHETIC,
        channel = com.gpsproxy.dev.domain.settings.InjectionChannel.CONTAINER,
    )

    @Test
    fun `single start transitions to running`() {
        val transition = machine.reduce(SimulationState(), SimulationCommand.StartSingle(singlePayload()))
        val state = (transition as SimulationTransition.Accepted).state
        assertEquals(SimMode.SINGLE, state.mode)
        assertEquals(RunState.RUNNING, state.runState)
        assertEquals(100L, state.startedAt)
    }

    @Test
    fun `second start while active is rejected`() {
        val active = (machine.reduce(
            SimulationState(),
            SimulationCommand.StartSingle(singlePayload()),
        ) as SimulationTransition.Accepted).state
        assertTrue(machine.reduce(active, SimulationCommand.StartRoute(routePayload())) is SimulationTransition.Rejected)
    }

    @Test
    fun `pause and resume only work in the right state`() {
        val idle = SimulationState()
        assertTrue(machine.reduce(idle, SimulationCommand.Pause) is SimulationTransition.Rejected)

        val running = (machine.reduce(idle, SimulationCommand.StartSingle(singlePayload()))
            as SimulationTransition.Accepted).state
        val paused = (machine.reduce(running, SimulationCommand.Pause)
            as SimulationTransition.Accepted).state
        assertEquals(RunState.PAUSED, paused.runState)
        assertEquals(0.0, paused.speedKmh, 0.0)
        assertTrue(machine.reduce(paused, SimulationCommand.Pause) is SimulationTransition.Rejected)

        val resumed = (machine.reduce(paused, SimulationCommand.Resume)
            as SimulationTransition.Accepted).state
        assertEquals(RunState.RUNNING, resumed.runState)
    }

    @Test
    fun `route resume restores configured speed`() {
        val running = (machine.reduce(SimulationState(), SimulationCommand.StartRoute(routePayload()))
            as SimulationTransition.Accepted).state
        val paused = (machine.reduce(running, SimulationCommand.Pause)
            as SimulationTransition.Accepted).state
        val resumed = (machine.reduce(paused, SimulationCommand.Resume)
            as SimulationTransition.Accepted).state
        assertEquals(32.0, resumed.speedKmh, 0.0)
    }

    @Test
    fun `complete only applies to running routes`() {
        val single = (machine.reduce(SimulationState(), SimulationCommand.StartSingle(singlePayload()))
            as SimulationTransition.Accepted).state
        assertTrue(machine.reduce(single, SimulationCommand.Complete) is SimulationTransition.Rejected)

        val route = (machine.reduce(SimulationState(), SimulationCommand.StartRoute(routePayload()))
            as SimulationTransition.Accepted).state
        val completed = (machine.reduce(route, SimulationCommand.Complete)
            as SimulationTransition.Accepted).state
        assertEquals(RunState.COMPLETED, completed.runState)
        assertEquals(0.0, completed.speedKmh, 0.0)
    }

    @Test
    fun `restart only applies to completed routes`() {
        val route = (machine.reduce(SimulationState(), SimulationCommand.StartRoute(routePayload()))
            as SimulationTransition.Accepted).state
        assertTrue(machine.reduce(route, SimulationCommand.Restart) is SimulationTransition.Rejected)

        val completed = (machine.reduce(route, SimulationCommand.Complete)
            as SimulationTransition.Accepted).state.copy(
                progress = RouteProgress(distanceDoneM = 100.0, distanceTotalM = 100.0, fraction = 1f),
                speedFactor = 2f,
            )
        val restarted = (machine.reduce(completed, SimulationCommand.Restart)
            as SimulationTransition.Accepted).state
        assertEquals(RunState.RUNNING, restarted.runState)
        assertEquals(0f, restarted.progress!!.fraction, 0f)
        assertEquals(0.0, restarted.progress!!.distanceDoneM, 0.0)
        assertEquals(64.0, restarted.speedKmh, 0.0)
    }

    @Test
    fun `route resume multiplies the configured speed by the factor`() {
        val running = (machine.reduce(SimulationState(), SimulationCommand.StartRoute(routePayload()))
            as SimulationTransition.Accepted).state.copy(speedFactor = 2f)
        val paused = (machine.reduce(running, SimulationCommand.Pause)
            as SimulationTransition.Accepted).state
        val resumed = (machine.reduce(paused, SimulationCommand.Resume)
            as SimulationTransition.Accepted).state
        assertEquals(64.0, resumed.speedKmh, 0.0)
    }

    @Test
    fun `stop resets ephemeral fields but keeps selection`() {
        val running = (machine.reduce(SimulationState(), SimulationCommand.StartSingle(singlePayload()))
            as SimulationTransition.Accepted).state.copy(address = "西湖", point = point)
        val stopped = (machine.reduce(running, SimulationCommand.Stop)
            as SimulationTransition.Accepted).state
        assertEquals(SimMode.IDLE, stopped.mode)
        assertEquals(RunState.IDLE, stopped.runState)
        assertEquals(point, stopped.point)
        assertEquals("西湖", stopped.address)
        assertEquals(null, stopped.injectedPoint)
        assertEquals(null, stopped.route)
        assertEquals(0, stopped.blurRadiusMeters)
    }

    @Test
    fun `abort returns to idle with error`() {
        val state = (machine.reduce(
            SimulationState(),
            SimulationCommand.Abort(ErrorCode.BOX_0101, "unavailable"),
        ) as SimulationTransition.Accepted).state
        assertEquals(SimMode.IDLE, state.mode)
        assertEquals(RunState.ERROR, state.runState)
        assertEquals(ErrorCode.BOX_0101, state.errorCode)
    }
}
