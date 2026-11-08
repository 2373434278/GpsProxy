package com.gpsproxy.dev.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePathTest {

    private val start = GeoPoint(30.250213, 120.148832)

    private fun northward(segments: Int, metersEach: Int): List<GeoPoint> =
        (0..segments).map { GeoUtils.offsetMeters(start, (it * metersEach).toDouble(), 0.0) }

    @Test
    fun `total distance accumulates segments`() {
        val path = RoutePath(northward(4, 250))
        assertEquals(1000.0, path.totalDistance, 1.0)
        assertEquals(4, path.segmentBearings.size)
        assertTrue(path.segmentBearings.all { it < 1f || it > 359f })
    }

    @Test
    fun `positionAt clamps to path bounds`() {
        val path = RoutePath(northward(2, 500))
        val first = requireNotNull(path.positionAt(-10.0))
        val last = requireNotNull(path.positionAt(10_000.0))
        assertEquals(0.0, GeoUtils.distanceMeters(path.points.first(), first.point), 1e-6)
        assertEquals(0.0, GeoUtils.distanceMeters(path.points.last(), last.point), 1e-6)
    }

    @Test
    fun `positionAt interpolates halfway`() {
        val path = RoutePath(northward(1, 1000))
        val middle = path.positionAt(500.0)
        requireNotNull(middle)
        assertEquals(500.0, GeoUtils.distanceMeters(start, middle.point), 1.0)
        assertEquals(500.0, middle.distanceAlong, 0.5)
    }

    @Test
    fun `single point path stays at zero`() {
        val path = RoutePath(listOf(start))
        assertEquals(0.0, path.totalDistance, 0.0)
        val position = path.positionAt(100.0)
        requireNotNull(position)
        assertEquals(start, position.point)
        assertEquals(0f, position.bearing, 0f)
    }

    @Test
    fun `empty path has no position`() {
        val path = RoutePath(emptyList())
        assertNull(path.positionAt(0.0))
    }
}
