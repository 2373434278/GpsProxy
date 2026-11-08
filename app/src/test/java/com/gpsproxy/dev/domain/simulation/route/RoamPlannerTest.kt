package com.gpsproxy.dev.domain.simulation.route

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.testing.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoamPlannerTest {

    private val anchor = GeoPoint(30.250213, 120.148832)

    @Test
    fun `plan is a closed loop inside the requested radius`() {
        val planner = RoamPlanner(SeededRandomSource(7))
        val plan = planner.plan(RoamConfig(anchor, radiusMeters = 400, speedKmh = 5))

        assertEquals(RouteSourceKind.ROAM, plan.source)
        assertEquals(RoamConfig(anchor, 400).vertices + 2, plan.points.size)
        assertEquals(anchor, plan.points.first())
        assertEquals(anchor, plan.points.last())
        plan.points.drop(1).dropLast(1).forEach { point ->
            assertTrue(
                "point=$point distance=${GeoUtils.distanceMeters(anchor, point)}",
                GeoUtils.distanceMeters(anchor, point) <= 400.0 + 0.5,
            )
        }
    }

    @Test
    fun `same seed produces the same loop`() {
        val first = RoamPlanner(SeededRandomSource(42)).plan(RoamConfig(anchor, 300))
        val second = RoamPlanner(SeededRandomSource(42)).plan(RoamConfig(anchor, 300))
        assertEquals(first.points, second.points)
    }

    @Test
    fun `radius is clamped to the supported range`() {
        val planner = RoamPlanner(SeededRandomSource(1))
        val tiny = planner.plan(RoamConfig(anchor, radiusMeters = 1))
        val huge = planner.plan(RoamConfig(anchor, radiusMeters = 99_999))
        // 极小半径被钳到 50m，不会退化成 0 长度路径
        assertTrue(GeoUtils.distanceMeters(anchor, tiny.points[1]) <= RoamPlanner.MIN_RADIUS_METERS + 1.0)
        assertTrue(GeoUtils.distanceMeters(anchor, huge.points[1]) <= RoamPlanner.MAX_RADIUS_METERS + 1.0)
    }

    @Test
    fun `vertices are ordered by angle around the anchor`() {
        val plan = RoamPlanner(SeededRandomSource(3)).plan(RoamConfig(anchor, 500, vertices = 16))
        val bearings = plan.points.drop(1).dropLast(1).map { GeoUtils.bearingDegrees(anchor, it) }
        bearings.zipWithNext().forEach { (a, b) ->
            val delta = (b - a + 360f) % 360f
            assertTrue("bearing should increase: $a -> $b", delta in 1f..60f)
        }
    }
}
