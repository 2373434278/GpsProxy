package com.gpsproxy.dev.domain.track

import com.gpsproxy.dev.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxCodecTest {

    private val basic = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <trk>
            <name>西湖环线</name>
            <trkseg>
              <trkpt lat="30.250213" lon="120.148832"><ele>12.5</ele></trkpt>
              <trkpt lat="30.250500" lon="120.149000"/>
              <trkpt lat="30.250900" lon="120.149400"><ele>13.2</ele></trkpt>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    @Test
    fun `parses track points name and elevation`() {
        val track = requireNotNull(GpxCodec.parse(basic))
        assertEquals("西湖环线", track.name)
        assertEquals(3, track.points.size)
        assertEquals(GeoPoint(30.250213, 120.148832), track.points.first())
        assertEquals(listOf(12.5, null, 13.2), track.elevations)
    }

    @Test
    fun `parses namespaced prefixes and route points`() {
        val xml = """
            <gpx:gpx xmlns:gpx="http://www.topografix.com/GPX/1/1">
              <gpx:rte>
                <gpx:rtept lat="30.1" lon="120.1"/>
                <gpx:rtept lat="30.2" lon="120.2"/>
              </gpx:rte>
            </gpx:gpx>
        """.trimIndent()
        val track = requireNotNull(GpxCodec.parse(xml))
        assertEquals(2, track.points.size)
        assertEquals(GeoPoint(30.2, 120.2), track.points.last())
    }

    @Test
    fun `skips invalid and duplicate consecutive points`() {
        val xml = """
            <gpx>
              <trk><trkseg>
                <trkpt lat="30.0" lon="120.0"/>
                <trkpt lat="30.0" lon="120.0"/>
                <trkpt lat="999" lon="120.0"/>
                <trkpt lat="30.1" lon="120.1"/>
                <trkpt lat="30.1" lon="120.1"/>
                <trkpt lat="30.2" lon="120.2"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        val track = requireNotNull(GpxCodec.parse(xml))
        assertEquals(3, track.points.size)
    }

    @Test
    fun `rejects empty short and malformed inputs`() {
        assertNull(GpxCodec.parse(""))
        assertNull(GpxCodec.parse("<gpx></gpx>"))
        assertNull(GpxCodec.parse("<gpx><trk><trkseg><trkpt lat=\"30\" lon=\"120\"/></trkseg></trk></gpx>"))
        assertNull(GpxCodec.parse("<gpx><trk>"))
        assertNull(GpxCodec.parse("not xml"))
    }

    @Test
    fun `rejects doctype and oversized payloads`() {
        val xxe = """
            <?xml version="1.0"?>
            <!DOCTYPE gpx [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <gpx><trk><trkseg>
              <trkpt lat="30" lon="120"/><trkpt lat="30.1" lon="120.1"/>
            </trkseg></trk></gpx>
        """.trimIndent()
        assertNull(GpxCodec.parse(xxe))
        assertNull(GpxCodec.parse("x".repeat(GpxCodec.MAX_XML_CHARS + 1)))
    }

    @Test
    fun `caps very long tracks`() {
        val xml = buildString {
            append("<gpx><trk><trkseg>")
            repeat(GpxCodec.MAX_POINTS + 500) { index ->
                append("<trkpt lat=\"").append(30.0 + index * 1e-5).append("\" lon=\"120.0\"/>")
            }
            append("</trkseg></trk></gpx>")
        }
        val track = requireNotNull(GpxCodec.parse(xml))
        assertEquals(GpxCodec.MAX_POINTS, track.points.size)
    }

    @Test
    fun `write then parse round trips`() {
        val points = listOf(
            GeoPoint(30.250213, 120.148832),
            GeoPoint(30.250500, 120.149000),
            GeoPoint(30.250900, 120.149400),
        )
        val elevations = listOf<Double?>(12.5, null, 13.2)
        val xml = GpxCodec.write("A&B <test>", points, elevations)
        val track = requireNotNull(GpxCodec.parse(xml))
        assertEquals("A&B <test>", track.name)
        assertEquals(points.size, track.points.size)
        points.zip(track.points).forEach { (expected, actual) ->
            assertEquals(expected.lat, actual.lat, 1e-6)
            assertEquals(expected.lng, actual.lng, 1e-6)
        }
        assertEquals(elevations, track.elevations)
        assertTrue(xml.contains("<gpx version=\"1.1\""))
        assertNotNull(track)
    }
}
