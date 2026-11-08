package com.gpsproxy.dev.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebMercatorTest {

    private val point = GeoPoint(30.250213, 120.148832)

    @Test
    fun `project and unproject round trip`() {
        for (zoom in listOf(3f, 10f, 15f, 19f)) {
            val projected = WebMercator.project(point, zoom)
            val restored = WebMercator.unproject(projected, zoom)
            assertEquals(point.lat, restored.lat, 1e-9)
            assertEquals(point.lng, restored.lng, 1e-9)
        }
    }

    @Test
    fun `screen conversion round trip`() {
        val center = GeoPoint(30.0, 120.0)
        val screen = WebMercator.toScreen(point, center, 15f, widthPx = 1080, heightPx = 1920)
        val restored = WebMercator.toGeo(screen.x, screen.y, center, 15f, 1080, 1920)
        assertEquals(point.lat, restored.lat, 1e-9)
        assertEquals(point.lng, restored.lng, 1e-9)
    }

    @Test
    fun `center maps to canvas center`() {
        val center = GeoPoint(30.0, 120.0)
        val screen = WebMercator.toScreen(center, center, 12f, 800, 600)
        assertEquals(400.0, screen.x, 1e-9)
        assertEquals(300.0, screen.y, 1e-9)
    }

    @Test
    fun `zoom is clamped and latitude is bounded`() {
        assertEquals(WebMercator.MIN_ZOOM, WebMercator.clampZoom(-2f), 0f)
        assertEquals(WebMercator.MAX_ZOOM, WebMercator.clampZoom(30f), 0f)
        val polar = WebMercator.project(GeoPoint(89.0, 0.0), 10f)
        val restored = WebMercator.unproject(polar, 10f)
        assertTrue(restored.lat <= 85.06)
    }
}
