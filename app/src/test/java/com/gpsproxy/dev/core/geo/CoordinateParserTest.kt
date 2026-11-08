package com.gpsproxy.dev.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateParserTest {

    @Test
    fun `parses decimal pairs in both orders`() {
        val normal = requireNotNull(CoordinateParser.parse("30.250213, 120.148832"))
        assertEquals(30.250213, normal.point.lat, 1e-9)
        assertEquals(120.148832, normal.point.lng, 1e-9)
        assertNull(normal.coordSystem)

        val swapped = requireNotNull(CoordinateParser.parse("120.148832,30.250213"))
        assertEquals(30.250213, swapped.point.lat, 1e-9)
        assertEquals(120.148832, swapped.point.lng, 1e-9)

        val spaces = requireNotNull(CoordinateParser.parse("30.250213 120.148832"))
        assertEquals(30.250213, spaces.point.lat, 1e-9)

        val semicolon = requireNotNull(CoordinateParser.parse("30.250213;120.148832"))
        assertEquals(120.148832, semicolon.point.lng, 1e-9)
    }

    @Test
    fun `parses dms with leading and trailing hemispheres`() {
        val leading = requireNotNull(
            CoordinateParser.parse("N30°15'00.8\" E120°08'55.7\""),
        )
        assertEquals(30.25022, leading.point.lat, 1e-4)
        assertEquals(120.14881, leading.point.lng, 1e-4)

        val trailing = requireNotNull(
            CoordinateParser.parse("30°15.013'N 120°08.929'E"),
        )
        assertEquals(30.25022, trailing.point.lat, 1e-4)
        assertEquals(120.14882, trailing.point.lng, 1e-4)

        val southWest = requireNotNull(
            CoordinateParser.parse("33°51'54.0\"S 151°12'36.0\"W"),
        )
        assertEquals(-33.865, southWest.point.lat, 1e-3)
        assertEquals(-151.21, southWest.point.lng, 1e-3)
    }

    @Test
    fun `geo uri is wgs84 and amap uri is gcj02`() {
        val geo = requireNotNull(CoordinateParser.parse("geo:30.250213,120.148832"))
        assertEquals(CoordSystem.WGS84, geo.coordSystem)
        assertEquals(30.250213, geo.point.lat, 1e-9)

        val amap = requireNotNull(
            CoordinateParser.parse("https://uri.amap.com/marker?position=120.148832,30.250213&name=x"),
        )
        assertEquals(CoordSystem.GCJ02, amap.coordSystem)
        assertEquals(30.250213, amap.point.lat, 1e-9)
        assertEquals(120.148832, amap.point.lng, 1e-9)
    }

    @Test
    fun `rejects unparsable and out of range input`() {
        assertNull(CoordinateParser.parse(""))
        assertNull(CoordinateParser.parse("   "))
        assertNull(CoordinateParser.parse("西湖"))
        assertNull(CoordinateParser.parse("91,120"))
        assertNull(CoordinateParser.parse("30,181"))
        assertNull(CoordinateParser.parse("30"))
    }

    @Test
    fun `accepts origin and boundary values`() {
        val origin = requireNotNull(CoordinateParser.parse("0,0"))
        assertEquals(0.0, origin.point.lat, 0.0)
        assertEquals(0.0, origin.point.lng, 0.0)

        val boundary = requireNotNull(CoordinateParser.parse("89.9,179.9"))
        assertEquals(89.9, boundary.point.lat, 1e-9)
    }
}
