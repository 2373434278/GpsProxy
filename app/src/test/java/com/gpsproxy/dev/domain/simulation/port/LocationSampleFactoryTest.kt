package com.gpsproxy.dev.domain.simulation.port

import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.testing.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSampleFactoryTest {

    private val factory = LocationSampleFactory(SeededRandomSource())
    private val point = GeoPoint(30.25, 120.15)

    @Test
    fun `static samples use the static satellite band`() {
        repeat(20) {
            val sample = factory.sample(point, CoordSystem.GCJ02, 5, 0f, 0f, 1L)
            assertTrue("sat=${sample.satellites}", sample.satellites in 11..12)
            assertEquals(5, sample.accuracyMeters)
            assertEquals(point, sample.point)
            assertTrue(sample.altitude in 11.0..13.0)
        }
    }

    @Test
    fun `moving samples use the moving satellite band`() {
        repeat(20) {
            val sample = factory.sample(point, CoordSystem.GCJ02, 5, 5f, 90f, 1L)
            assertTrue("sat=${sample.satellites}", sample.satellites in 9..11)
            assertEquals(90f, sample.bearing, 0f)
        }
    }

    @Test
    fun `altitude override centers the jitter`() {
        repeat(20) {
            val sample = factory.sample(
                point,
                CoordSystem.GCJ02,
                5,
                0f,
                0f,
                1L,
                altitudeMeters = 500.0,
            )
            assertTrue("altitude=${sample.altitude}", sample.altitude in 499.0..501.0)
        }
    }

    @Test
    fun `drift stays inside the requested radius`() {
        repeat(50) {
            val drifted = factory.drift(point, 1.25)
            assertTrue(GeoUtils.distanceMeters(point, drifted) <= 1.25 + 1e-6)
        }
    }

    @Test
    fun `drift is deterministic for a seeded source`() {
        val other = LocationSampleFactory(SeededRandomSource())
        assertEquals(factory.drift(point, 5.0), other.drift(point, 5.0))
    }
}
