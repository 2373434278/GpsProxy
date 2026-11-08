package com.gpsproxy.dev.domain.simulation.blur

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BlurPolicyTest {

    private val anchor = GeoPoint(30.250213, 120.148832)

    @Test
    fun `zero radius is a no-op`() {
        val policy = BlurPolicy.of(0)
        assertEquals(anchor, policy.injectedPoint(anchor))
        assertEquals(0, policy.radiusMeters)
        val points = listOf(anchor, GeoUtils.offsetMeters(anchor, 100.0, 0.0))
        assertEquals(points, policy.translate(points))
    }

    @Test
    fun `offset is deterministic for same anchor and radius`() {
        val first = BlurPolicy.of(200).injectedPoint(anchor)
        val second = BlurPolicy.of(200).injectedPoint(anchor)
        assertEquals(first, second)
    }

    @Test
    fun `injected point stays inside the radius`() {
        val injected = BlurPolicy.of(500).injectedPoint(anchor)
        assertTrue(GeoUtils.distanceMeters(anchor, injected) <= 500.0 + 1e-6)
    }

    @Test
    fun `offset is reused when anchor changes during a session`() {
        val policy = BlurPolicy.of(300)
        val injectedA = policy.injectedPoint(anchor)
        val otherAnchor = GeoUtils.offsetMeters(anchor, 40.0, -30.0)
        val injectedB = policy.injectedPoint(otherAnchor)

        val (northA, eastA) = GeoUtils.projectMeters(anchor, injectedA)
        val (northB, eastB) = GeoUtils.projectMeters(otherAnchor, injectedB)
        assertEquals(northA, northB, 0.01)
        assertEquals(eastA, eastB, 0.01)
    }

    @Test
    fun `route translation preserves shape and length`() {
        val points = (0..10).map { GeoUtils.offsetMeters(anchor, it * 100.0, it * 20.0) }
        val original = com.gpsproxy.dev.core.geo.RoutePath(points)
        val translated = com.gpsproxy.dev.core.geo.RoutePath(BlurPolicy.of(200).translate(points))
        assertEquals(original.points.size, translated.points.size)
        assertEquals(original.totalDistance, translated.totalDistance, original.totalDistance * 0.005)
        for (index in 0 until points.lastIndex) {
            val originalSegment = GeoUtils.distanceMeters(points[index], points[index + 1])
            val movedSegment = GeoUtils.distanceMeters(translated.points[index], translated.points[index + 1])
            assertEquals(originalSegment, movedSegment, originalSegment * 0.005)
        }
    }

    @Test
    fun `offset magnitude never exceeds radius for various radii`() {
        for (radius in listOf(50, 100, 200, 500, 1000, 5000)) {
            val injected = BlurPolicy.of(radius).injectedPoint(anchor)
            val (north, east) = GeoUtils.projectMeters(anchor, injected)
            assertTrue("radius=$radius", hypot(north, east) <= radius + 1.0)
        }
    }
}
