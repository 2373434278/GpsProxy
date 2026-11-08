package com.gpsproxy.dev.data.route

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.domain.simulation.model.TravelMode
import com.gpsproxy.dev.domain.simulation.route.FallbackRouteSource
import com.gpsproxy.dev.domain.simulation.route.RouteRequest
import com.gpsproxy.dev.domain.simulation.route.RouteSourceKind
import com.gpsproxy.dev.domain.simulation.route.SyntheticRouteSource
import com.gpsproxy.dev.testing.FakeHttpTextClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AmapRouteSourceTest {

    private val start = GeoPoint(30.250213, 120.148832)
    private val end = GeoPoint(30.260000, 120.160000)
    private val waypoint = GeoPoint(30.255000, 120.154000)

    private val drivingJson = """
        {"status":"1","route":{"paths":[{"steps":[
          {"polyline":"120.148832,30.250213;120.150000,30.252000"},
          {"polyline":"120.150000,30.252000;120.160000,30.260000"}
        ]}]}}
    """.trimIndent()

    /** v4 骑行信封：errcode/data，没有 status/route。用真实形状才能覆盖解析分支。 */
    private val cyclingJson = """
        {"errcode":0,"errmsg":"OK","errdetail":null,"data":{"paths":[{"steps":[
          {"polyline":"120.148832,30.250213;120.150000,30.252000"},
          {"polyline":"120.150000,30.252000;120.160000,30.260000"}
        ]}]}}
    """.trimIndent()

    private fun request(
        mode: TravelMode = TravelMode.DRIVING,
        waypoints: List<GeoPoint> = emptyList(),
    ): RouteRequest = RouteRequest(start, end, waypoints, mode)

    @Test
    fun `driving response is parsed into a plan`() = runBlocking {
        val client = FakeHttpTextClient { _, _ -> drivingJson }
        val source = AmapRouteSource(client, "key")
        val plan = source.plan(request())

        requireNotNull(plan)
        assertEquals(RouteSourceKind.NETWORK, plan.source)
        assertEquals(3, plan.points.size)
        assertTrue(client.lastUrl.contains("/v3/direction/driving"))
        assertEquals("120.148832,30.250213", client.lastParams["origin"])
        assertEquals("120.16,30.26", client.lastParams["destination"])
        assertTrue(client.lastParams["waypoints"] == null)
    }

    @Test
    fun `driving passes waypoints in a single request`() = runBlocking {
        val client = FakeHttpTextClient { _, _ -> drivingJson }
        val source = AmapRouteSource(client, "key")
        source.plan(request(waypoints = listOf(waypoint)))

        assertEquals(1, client.calls)
        assertEquals("120.154,30.255", client.lastParams["waypoints"])
    }

    @Test
    fun `cycling requests each leg and concatenates`() = runBlocking {
        val client = FakeHttpTextClient { _, _ -> cyclingJson }
        val source = AmapRouteSource(client, "key")
        val plan = source.plan(request(mode = TravelMode.CYCLING, waypoints = listOf(waypoint)))

        requireNotNull(plan)
        // 起点→途经点、途经点→终点，两段请求；骑行接口不支持 waypoints 参数。
        assertEquals(2, client.calls)
        assertTrue(client.lastUrl.contains("/v4/direction/bicycling"))
        assertTrue(client.lastParams["waypoints"] == null)
        assertEquals(3, plan.points.size)
        assertEquals(RouteSourceKind.NETWORK, plan.source)
    }

    @Test
    fun `walking and cycling use their own endpoints`() = runBlocking {
        val client = FakeHttpTextClient { url, _ ->
            if (url.contains("bicycling")) cyclingJson else drivingJson
        }
        val source = AmapRouteSource(client, "key")
        source.plan(request(mode = TravelMode.WALKING))
        assertTrue(client.lastUrl.contains("/v3/direction/walking"))
        source.plan(request(mode = TravelMode.CYCLING))
        assertTrue(client.lastUrl.contains("/v4/direction/bicycling"))
    }

    @Test
    fun `v4 bicycling error envelope throws with its reason`() = runBlocking {
        val source = AmapRouteSource(
            FakeHttpTextClient { _, _ -> """{"errcode":20003,"errmsg":"INVALID_USER_KEY"}""" },
            "bad",
        )
        try {
            source.plan(request(mode = TravelMode.CYCLING))
            org.junit.Assert.fail("expected IOException")
        } catch (error: IOException) {
            assertEquals("INVALID_USER_KEY", error.message)
        }
    }

    @Test
    fun `blank key produces no plan`() = runBlocking {
        val source = AmapRouteSource(FakeHttpTextClient { _, _ -> drivingJson }, "")
        assertNull(source.plan(request()))
    }

    @Test
    fun `error payload throws`() = runBlocking {
        val source = AmapRouteSource(
            FakeHttpTextClient { _, _ -> """{"status":"0","info":"INVALID_KEY"}""" },
            "bad",
        )
        try {
            source.plan(request())
            org.junit.Assert.fail("expected IOException")
        } catch (_: IOException) {
            // 由 FallbackRouteSource 负责回退
        }
    }

    @Test
    fun `fallback returns synthetic route when network source fails`() = runBlocking {
        val failing = object : com.gpsproxy.dev.domain.simulation.route.RouteSource {
            override suspend fun plan(
                request: RouteRequest,
            ): com.gpsproxy.dev.domain.simulation.route.RoutePlan = throw IOException("boom")
        }
        val source = FallbackRouteSource(failing, SyntheticRouteSource())
        val plan = requireNotNull(source.plan(request(waypoints = listOf(waypoint))))
        assertEquals(RouteSourceKind.SYNTHETIC, plan.source)
        assertTrue(plan.points.size >= 2)
        // 回退必须带原因，UI/日志才能解释为什么不是真实路网。
        assertEquals("boom", plan.degradeReason)
    }

    @Test
    fun `successful network plan carries no degrade reason`() = runBlocking {
        val source = FallbackRouteSource(
            AmapRouteSource(FakeHttpTextClient { _, _ -> cyclingJson }, "key"),
            SyntheticRouteSource(),
        )
        val plan = requireNotNull(source.plan(request(mode = TravelMode.CYCLING)))
        assertEquals(RouteSourceKind.NETWORK, plan.source)
        assertNull(plan.degradeReason)
    }
}
