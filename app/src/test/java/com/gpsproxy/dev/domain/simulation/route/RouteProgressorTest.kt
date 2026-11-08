package com.gpsproxy.dev.domain.simulation.route

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.core.geo.RoutePath
import com.gpsproxy.dev.domain.simulation.blur.BlurPolicy
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressorTest {

    private val progressor = RouteProgressor()
    private val start = GeoPoint(30.250213, 120.148832)

    /** 正北方向 [meters] 米的两点轨迹。 */
    private fun line(meters: Double): RoutePath =
        RoutePath(listOf(start, GeoUtils.offsetMeters(start, meters, 0.0)))

    private fun config(
        speedKmh: Int = 36,
        loop: Boolean = false,
        roundTrip: Boolean = false,
        meters: Double = 100.0,
    ): RouteConfig = RouteConfig(
        start = start,
        end = GeoUtils.offsetMeters(start, meters, 0.0),
        mode = TravelMode.DRIVING,
        speedKmh = speedKmh,
        loop = loop,
        roundTrip = roundTrip,
    )

    @Test
    fun `one way trip advances by elapsed time`() {
        val runtime = progressor.createRuntime(config(), line(100.0), BlurPolicy.of(0))
        val first = requireNotNull(progressor.advance(runtime, 5_000L))
        assertEquals(50.0, first.progress.distanceDoneM, 0.5)
        assertFalse(first.completed)
        assertEquals(10f, first.speedMps, 0.01f)

        val second = requireNotNull(progressor.advance(runtime, 6_000L))
        assertTrue(second.completed)
        assertEquals(100.0, second.progress.distanceDoneM, 0.5)
        assertEquals(1f, second.progress.fraction, 0.001f)
        assertEquals(0f, second.speedMps, 0f)
        assertEquals(0, second.progress.remainingSeconds)
    }

    @Test
    fun `loop restarts at origin without completing`() {
        val runtime = progressor.createRuntime(config(loop = true), line(100.0), BlurPolicy.of(0))
        val advance = requireNotNull(progressor.advance(runtime, 11_000L))
        assertFalse(advance.completed)
        assertEquals(0.0, runtime.travelledMeters, 0.5)
        assertEquals(0f, advance.progress.fraction, 0.001f)
    }

    @Test
    fun `round trip reverses bearing after the midpoint`() {
        val runtime = progressor.createRuntime(
            config(roundTrip = true),
            line(100.0),
            BlurPolicy.of(0),
        )
        val advance = requireNotNull(progressor.advance(runtime, 15_000L))
        assertFalse(advance.completed)
        assertEquals(150.0, advance.progress.distanceDoneM, 0.5)
        assertEquals(200.0, advance.progress.distanceTotalM, 0.5)
        assertEquals(0.75f, advance.progress.fraction, 0.01f)
        assertEquals(180f, advance.bearing, 1f)
        // 回程时目标点应回到起点与终点之间，而不是继续向北
        assertEquals(50.0, GeoUtils.distanceMeters(start, advance.targetPoint), 1.5)
    }

    @Test
    fun `zero length route cannot advance`() {
        val path = RoutePath(listOf(start, start))
        val runtime = progressor.createRuntime(config(meters = 0.0), path, BlurPolicy.of(0))
        assertNull(progressor.advance(runtime, 1_000L))
    }

    @Test
    fun `blurred runtime keeps the same progress denominator`() {
        val base = line(1_000.0)
        val runtime = progressor.createRuntime(config(), base, BlurPolicy.of(200))
        assertEquals(base.totalDistance, runtime.path.totalDistance, base.totalDistance * 0.005)
        val advance = requireNotNull(progressor.advance(runtime, 10_000L))
        assertEquals(runtime.path.totalDistance, advance.progress.distanceTotalM, 0.5)
    }

    @Test
    fun `rebinding blur keeps travelled distance`() {
        val runtime = progressor.createRuntime(config(), line(1_000.0), BlurPolicy.of(0))
        progressor.advance(runtime, 10_000L)
        val travelled = runtime.travelledMeters
        progressor.rebindBlur(runtime, BlurPolicy.of(500))
        assertEquals(travelled, runtime.travelledMeters, 0.0)
        assertEquals(1_000.0, runtime.path.totalDistance, 5.0)
    }

    @Test
    fun `speed factor multiplies travelled distance`() {
        val runtime = progressor.createRuntime(config(), line(100.0), BlurPolicy.of(0))
        runtime.speedFactor = 2f
        val advance = requireNotNull(progressor.advance(runtime, 2_000L))
        assertEquals(40.0, advance.progress.distanceDoneM, 0.5)
        assertFalse(advance.completed)
        assertEquals(20f, advance.speedMps, 0.01f)
    }

    @Test
    fun `seek clamps and recomputes remaining time`() {
        val runtime = progressor.createRuntime(config(), line(100.0), BlurPolicy.of(0))
        val advance = requireNotNull(progressor.seek(runtime, 0.25f))
        assertEquals(25.0, advance.progress.distanceDoneM, 0.5)
        assertEquals(0.25f, advance.progress.fraction, 0.01f)
        assertEquals(25.0, runtime.travelledMeters, 0.5)
        // 10 m/s 走完剩余 75m 需要 7.5 秒取整
        assertEquals(7, advance.progress.remainingSeconds)
        assertEquals(0.999f, progressor.seek(runtime, 2f)!!.progress.fraction, 0.001f)
    }

    @Test
    fun `reverse walks back to origin and completes`() {
        val runtime = progressor.createRuntime(config(), line(100.0), BlurPolicy.of(0))
        progressor.advance(runtime, 5_000L)
        runtime.direction = -1
        val advance = requireNotNull(progressor.advance(runtime, 2_000L))
        assertEquals(30.0, advance.progress.distanceDoneM, 0.5)
        assertEquals(180f, advance.bearing, 1f)
        assertEquals(3, advance.progress.remainingSeconds)

        val end = requireNotNull(progressor.advance(runtime, 10_000L))
        assertTrue(end.completed)
        assertEquals(0.0, end.progress.distanceDoneM, 0.5)
    }

    @Test
    fun `reverse in loop mode wraps to the far end`() {
        val runtime = progressor.createRuntime(config(loop = true), line(100.0), BlurPolicy.of(0))
        progressor.advance(runtime, 1_000L)
        runtime.direction = -1
        val advance = requireNotNull(progressor.advance(runtime, 2_000L))
        assertFalse(advance.completed)
        assertEquals(100.0, runtime.travelledMeters, 0.5)
    }

    @Test
    fun `reset returns runtime to the origin`() {
        val runtime = progressor.createRuntime(config(), line(100.0), BlurPolicy.of(0))
        progressor.advance(runtime, 5_000L)
        runtime.direction = -1
        progressor.reset(runtime)
        assertEquals(0.0, runtime.travelledMeters, 0.0)
        assertEquals(1, runtime.direction)
        assertEquals(10, progressor.remainingSeconds(runtime))
    }

    @Test
    fun `round trip reverse mapping flips bearing along the same leg`() {
        val runtime = progressor.createRuntime(
            config(roundTrip = true),
            line(100.0),
            BlurPolicy.of(0),
        )
        progressor.advance(runtime, 15_000L) // 150m，回程 50m 处
        runtime.direction = -1
        val advance = requireNotNull(progressor.advance(runtime, 2_000L))
        // 反向回程：沿单程向南（去程向北），方向与回程相反 => 航向 0°
        assertEquals(0f, advance.bearing, 1f)
        assertEquals(130.0, advance.progress.distanceDoneM, 0.5)
    }
}
