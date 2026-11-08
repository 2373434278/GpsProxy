package com.gpsproxy.dev.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapUriBuilderTest {

    private val point = GeoPoint(30.250213, 120.148832)

    @Test
    fun `geo uri uses lat lng order`() {
        val uri = MapUriBuilder.geoUri(point, "西湖")
        assertTrue(uri.startsWith("geo:30.250213,120.148832?"))
        assertTrue(uri.contains("q=30.250213,120.148832"))
        assertTrue(uri.contains("%E8%A5%BF%E6%B9%96"))
    }

    @Test
    fun `amap uri uses lng lat order`() {
        val uri = MapUriBuilder.amapUri(point, "西湖")
        assertTrue(uri.contains("position=120.148832,30.250213"))
        assertTrue(uri.contains("name=%E8%A5%BF%E6%B9%96"))
    }

    @Test
    fun `labels with spaces and special characters are encoded`() {
        val uri = MapUriBuilder.geoUri(point, "my spot & cafe")
        assertTrue(uri.contains("my%20spot%20%26%20cafe"))
    }
}
