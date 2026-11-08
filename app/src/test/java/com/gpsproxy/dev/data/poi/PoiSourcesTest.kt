package com.gpsproxy.dev.data.poi

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.data.net.HttpTextClient
import com.gpsproxy.dev.domain.place.PoiCategory
import com.gpsproxy.dev.domain.place.PoiQuery
import com.gpsproxy.dev.domain.place.PoiSource
import com.gpsproxy.dev.testing.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakeHttpTextClient(
    private val responder: (String, Map<String, String>) -> String,
) : HttpTextClient {
    var calls = 0
    var lastParams: Map<String, String> = emptyMap()

    override suspend fun get(baseUrl: String, params: Map<String, String>): String {
        calls++
        lastParams = params
        return responder(baseUrl, params)
    }
}

class PoiSourcesTest {

    private val center = GeoPoint(30.250213, 120.148832)

    private val amapJson = """
        {"status":"1","pois":[
          {"name":"西湖","location":"120.148832,30.250213","pname":"浙江省","cityname":"杭州市","adname":"西湖区","address":"龙井路","type":"风景名胜"},
          {"name":"断桥","location":"120.148720,30.262410","pname":"浙江省","cityname":"杭州市","adname":"西湖区","address":"北山街","type":"风景名胜"}
        ]}
    """.trimIndent()

    @Test
    fun `local source filters by keyword and marks source`() = runTest {
        val source = LocalPoiSource()
        val result = source.search(PoiQuery("西湖", PoiCategory.ALL, center, false, 1000, 0))
        assertTrue(result.items.isNotEmpty())
        assertEquals(PoiSource.LOCAL, result.source)
        assertNull(result.warning)
    }

    @Test
    fun `local source filters nearby radius`() = runTest {
        val source = LocalPoiSource()
        val result = source.search(PoiQuery("", PoiCategory.ALL, center, true, 200, 0))
        assertTrue(result.items.all { item ->
            com.gpsproxy.dev.core.geo.GeoUtils.distanceMeters(center, GeoPoint(item.lat, item.lng)) <= 200.0
        })
    }

    @Test
    fun `amap source parses response and infers category`() = runTest {
        val client = FakeHttpTextClient { _, _ -> amapJson }
        val source = AmapPoiSource(client, key = "test-key")
        val result = source.search(PoiQuery("西湖", PoiCategory.ALL, center, true, 1000, 0))
        assertEquals(PoiSource.AMAP, result.source)
        assertEquals(2, result.items.size)
        assertEquals(PoiCategory.SCENIC, result.items.first().category)
        assertEquals("120.148832", client.lastParams["location"]!!.substringBefore(","))
    }

    @Test
    fun `amap error payload throws`() = kotlinx.coroutines.runBlocking {
        val client = FakeHttpTextClient { _, _ -> """{"status":"0","info":"INVALID_KEY"}""" }
        val source = AmapPoiSource(client, key = "bad")
        try {
            source.search(PoiQuery("x", PoiCategory.ALL, center, false, 1000, 0))
            org.junit.Assert.fail("expected IOException")
        } catch (_: IOException) {
            // 预期：失败由上层 FallbackPoiSource 负责回退
        }
    }

    @Test
    fun `amap reverse geocode parses formatted address`() = runTest {
        val client = FakeHttpTextClient { url, _ ->
            if (url.contains("regeo")) {
                """{"status":"1","regeocode":{"formatted_address":"浙江省杭州市西湖区龙井路"}}"""
            } else {
                amapJson
            }
        }
        val source = AmapPoiSource(client, key = "k")
        assertEquals("浙江省杭州市西湖区龙井路", source.reverseGeocode(center))
    }

    @Test
    fun `fallback adds warning when live source fails`() = runTest {
        val failing = object : com.gpsproxy.dev.domain.place.PoiSearchSource {
            override val hasLiveSource = true
            override suspend fun search(query: PoiQuery): com.gpsproxy.dev.domain.place.PoiSearchResult =
                throw IOException("boom")
            override suspend fun reverseGeocode(point: GeoPoint): String? = null
        }
        val source = FallbackPoiSource(primary = failing, fallback = LocalPoiSource())
        val result = source.search(PoiQuery("西湖", PoiCategory.ALL, center, false, 1000, 0))
        assertEquals(PoiSource.LOCAL, result.source)
        assertNotNull(result.warning)
    }

    @Test
    fun `fallback without primary does not warn`() = runTest {
        val source = FallbackPoiSource(primary = null, fallback = LocalPoiSource())
        val result = source.search(PoiQuery("西湖", PoiCategory.ALL, center, false, 1000, 0))
        assertNull(result.warning)
    }

    @Test
    fun `cache avoids duplicate calls within ttl`() = runTest {
        var calls = 0
        val delegate = object : com.gpsproxy.dev.domain.place.PoiSearchSource {
            override val hasLiveSource = true
            override suspend fun search(query: PoiQuery): com.gpsproxy.dev.domain.place.PoiSearchResult {
                calls++
                return com.gpsproxy.dev.domain.place.PoiSearchResult(emptyList(), PoiSource.AMAP)
            }

            override suspend fun reverseGeocode(point: GeoPoint): String? = null
        }
        val clock = FakeClock()
        val cached = CachingPoiSource(delegate, clock, ttlMillis = 60_000)
        val query = PoiQuery("a", PoiCategory.ALL, center, false, 1000, 0)
        cached.search(query)
        cached.search(query)
        assertEquals(1, calls)

        clock.wall += 61_000
        cached.search(query)
        assertEquals(2, calls)
    }
}
