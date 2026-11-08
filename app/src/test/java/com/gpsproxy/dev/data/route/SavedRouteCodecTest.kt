package com.gpsproxy.dev.data.route

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.domain.simulation.model.TravelMode
import com.gpsproxy.dev.domain.simulation.route.SavedRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRouteCodecTest {

    private val start = GeoPoint(30.250213, 120.148832)
    private val end = GeoPoint(30.260000, 120.160000)

    private fun route(
        id: Long = 1L,
        kind: RouteKind = RouteKind.POINT_TO_POINT,
        favorite: Boolean = true,
    ) = SavedRoute(
        id = id,
        name = "测试路线",
        config = RouteConfig(
            start = start,
            end = end,
            mode = TravelMode.CYCLING,
            speedKmh = 18,
            loop = true,
            waypoints = listOf(GeoPoint(30.255, 120.154)),
            kind = kind,
            trackPoints = if (kind == RouteKind.IMPORTED) {
                listOf(start, GeoPoint(30.255, 120.154), end)
            } else {
                emptyList()
            },
            roamRadiusMeters = 800,
        ),
        favorite = favorite,
        createdAt = 100L,
        lastUsedAt = 200L,
    )

    @Test
    fun `encode then decode preserves the route`() {
        val routes = listOf(route(), route(id = 2, kind = RouteKind.ROAM, favorite = false))
        val decoded = SavedRouteCodec.decode(SavedRouteCodec.encode(routes))
        assertEquals(routes, decoded)
    }

    @Test
    fun `blank or broken json decodes to an empty list`() {
        assertTrue(SavedRouteCodec.decode(null).isEmpty())
        assertTrue(SavedRouteCodec.decode("").isEmpty())
        assertTrue(SavedRouteCodec.decode("{not-json").isEmpty())
        assertTrue(SavedRouteCodec.decode("""[{"name":"x"}]""").isEmpty())
    }

    @Test
    fun `invalid points and enum names fall back safely`() {
        val raw = """
            [{"id":1,"name":"x","favorite":false,"config":{
              "kind":"MARS","start":[30.25,120.15],"end":[30.26,120.16],
              "mode":"TELEPORT","speedKmh":-5,"waypoints":[[999,999]],"trackPoints":[],
              "roamRadius":1
            }}]
        """.trimIndent()
        val decoded = SavedRouteCodec.decode(raw).single()
        assertEquals(RouteKind.POINT_TO_POINT, decoded.config.kind)
        assertEquals(TravelMode.DRIVING, decoded.config.mode)
        assertEquals(1, decoded.config.speedKmh)
        assertTrue(decoded.config.waypoints.isEmpty())
        assertEquals(50, decoded.config.roamRadiusMeters)
        assertTrue(decoded.config.start.isValid())
    }

    @Test
    fun `long imported track is capped when persisted`() {
        val points = (0 until 5_000).map { GeoUtils.offsetMeters(start, it * 1.0, 0.0) }
        val long = SavedRoute(
            id = 9,
            name = "long",
            config = RouteConfig(
                start = points.first(),
                end = points.last(),
                kind = RouteKind.IMPORTED,
                trackPoints = points,
            ),
            favorite = false,
            createdAt = 1,
            lastUsedAt = 1,
        )
        val decoded = SavedRouteCodec.decode(SavedRouteCodec.encode(listOf(long))).single()
        assertEquals(2_000, decoded.config.trackPoints.size)
        assertEquals(points.first(), decoded.config.trackPoints.first())
        assertEquals(points.last(), decoded.config.trackPoints.last())
    }
}
