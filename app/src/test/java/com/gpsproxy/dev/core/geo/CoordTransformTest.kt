package com.gpsproxy.dev.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordTransformTest {

    private val hangzhou = GeoPoint(30.250213, 120.148832)
    private val outsideChina = GeoPoint(48.8566, 2.3522)

    @Test
    fun `same system is identity`() {
        assertEquals(hangzhou, CoordTransform.transform(hangzhou, CoordSystem.GCJ02, CoordSystem.GCJ02))
    }

    @Test
    fun `wgs and gcj round trip converges`() {
        val gcj = CoordTransform.wgs84ToGcj02(hangzhou)
        val back = CoordTransform.gcj02ToWgs84(gcj)
        assertEquals(hangzhou.lat, back.lat, 1e-6)
        assertEquals(hangzhou.lng, back.lng, 1e-6)
    }

    @Test
    fun `baidu round trip converges`() {
        val bd = CoordTransform.gcj02ToBd09(hangzhou)
        val back = CoordTransform.bd09ToGcj02(bd)
        assertEquals(hangzhou.lat, back.lat, 1e-6)
        assertEquals(hangzhou.lng, back.lng, 1e-6)
    }

    @Test
    fun `coordinates outside china are returned unchanged`() {
        assertEquals(outsideChina, CoordTransform.transform(outsideChina, CoordSystem.WGS84, CoordSystem.GCJ02))
        assertTrue(CoordTransform.outOfChina(outsideChina.lat, outsideChina.lng))
    }
}
