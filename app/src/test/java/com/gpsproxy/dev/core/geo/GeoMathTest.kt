package com.gpsproxy.dev.core.geo

import com.gpsproxy.dev.core.i18n.lang.ZhStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    @Test
    fun `distance at equator is about 111km per degree`() {
        val distance = GeoUtils.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        assertEquals(111_194.9, distance, 2.0)
    }

    @Test
    fun `bearing follows cardinal directions`() {
        val origin = GeoPoint(30.0, 120.0)
        assertEquals(0f, GeoUtils.bearingDegrees(origin, GeoPoint(30.1, 120.0)), 0.5f)
        assertEquals(90f, GeoUtils.bearingDegrees(origin, GeoPoint(30.0, 120.1)), 0.5f)
        assertEquals(180f, GeoUtils.bearingDegrees(origin, GeoPoint(29.9, 120.0)), 0.5f)
        assertEquals(270f, GeoUtils.bearingDegrees(origin, GeoPoint(30.0, 119.9)), 0.5f)
    }

    @Test
    fun `offsetMeters moves by the requested distance`() {
        val origin = GeoPoint(30.0, 120.0)
        val north = GeoUtils.offsetMeters(origin, 100.0, 0.0)
        val east = GeoUtils.offsetMeters(origin, 0.0, 100.0)
        assertEquals(100.0, GeoUtils.distanceMeters(origin, north), 0.05)
        assertEquals(100.0, GeoUtils.distanceMeters(origin, east), 0.05)
    }

    @Test
    fun `blur offset is deterministic and inside the radius`() {
        val point = GeoPoint(30.250213, 120.148832)
        val (north, east) = GeoUtils.blurOffsetMeters(point, 200.0)
        val (northAgain, eastAgain) = GeoUtils.blurOffsetMeters(point, 200.0)
        assertEquals(north, northAgain, 0.0)
        assertEquals(east, eastAgain, 0.0)
        val distance = kotlin.math.sqrt(north * north + east * east)
        assertTrue("offset=$distance", distance <= 200.0 + 1e-9)
    }

    @Test
    fun `formatting is human readable`() {
        assertEquals("850 m", GeoUtils.formatDistance(850.0))
        assertEquals("1.5 km", GeoUtils.formatDistance(1500.0))
        assertEquals("45 秒", GeoUtils.formatDuration(45, ZhStrings.route))
        assertEquals("2 分钟", GeoUtils.formatDuration(120, ZhStrings.route))
        assertEquals("1 小时 1 分", GeoUtils.formatDuration(3660, ZhStrings.route))
    }

    @Test
    fun `multi route passes through waypoints without duplicate seams`() {
        val start = GeoPoint(30.0, 120.0)
        val waypoint = GeoPoint(30.01, 120.01)
        val end = GeoPoint(30.02, 120.0)
        val points = GeoUtils.buildSyntheticMultiRoute(listOf(start, waypoint, end))
        assertTrue(points.size > 10)
        assertEquals(start, points.first())
        assertEquals(end, points.last())
        // 接缝点只出现一次；相邻重复点会破坏航向计算
        val duplicates = points.zipWithNext().count { it.first == it.second }
        assertEquals(0, duplicates)
        assertTrue(points.any { GeoUtils.distanceMeters(it, waypoint) < 5.0 })
    }

    @Test
    fun `downsample keeps first and last and respects the cap`() {
        val points = (0 until 1_000).map { GeoPoint(30.0 + it * 1e-5, 120.0) }
        val sampled = GeoUtils.downsample(points, 100)
        assertEquals(100, sampled.size)
        assertEquals(points.first(), sampled.first())
        assertEquals(points.last(), sampled.last())
        assertEquals(points, GeoUtils.downsample(points, 5_000))
    }

    @Test
    fun `circle path closes and stays on the radius`() {
        val center = GeoPoint(30.25, 120.15)
        val circle = GeoUtils.circlePath(center, 300.0, segments = 64)
        assertEquals(65, circle.size)
        assertEquals(circle.first(), circle.last())
        circle.forEach { point ->
            assertEquals(300.0, GeoUtils.distanceMeters(center, point), 1.0)
        }
    }
}
