package com.gpsproxy.dev.domain.simulation.route

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.core.i18n.lang.ZhStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteHistoryPolicyTest {

    private val start = GeoPoint(30.250213, 120.148832)

    private fun point(index: Int): GeoPoint = GeoUtils.offsetMeters(start, index * 20.0, 0.0)

    private fun config(index: Int): RouteConfig = RouteConfig(
        start = start,
        end = point(index + 1),
        speedKmh = 30,
    )

    @Test
    fun `same signature is recorded once and refreshes timestamp`() {
        var routes = emptyList<SavedRoute>()
        routes = RouteHistoryPolicy.recordRecent(routes, config(0), "a", now = 100L)
        routes = RouteHistoryPolicy.recordRecent(routes, config(0), "b", now = 200L)
        assertEquals(1, routes.size)
        assertEquals("a", routes.single().name)
        assertEquals(200L, routes.single().lastUsedAt)
    }

    @Test
    fun `speed change does not create a duplicate entry`() {
        var routes = RouteHistoryPolicy.recordRecent(emptyList(), config(0), "a", now = 1L)
        routes = RouteHistoryPolicy.recordRecent(
            routes,
            config(0).copy(speedKmh = 60, loop = true),
            "a",
            now = 2L,
        )
        assertEquals(1, routes.size)
        assertEquals(60, routes.single().config.speedKmh)
    }

    @Test
    fun `recent list is trimmed to ten but favorites survive`() {
        var routes = emptyList<SavedRoute>()
        routes = RouteHistoryPolicy.recordRecent(routes, config(0), "fav", now = 1L)
        routes = RouteHistoryPolicy.setFavorite(routes, routes.single().id, true)
        for (index in 1..14) {
            routes = RouteHistoryPolicy.recordRecent(routes, config(index), "r$index", now = index + 10L)
        }
        val favorites = routes.filter { it.favorite }
        val recents = routes.filterNot { it.favorite }
        assertEquals(1, favorites.size)
        assertEquals(RouteHistoryPolicy.MAX_RECENT, recents.size)
        assertEquals(1 + RouteHistoryPolicy.MAX_RECENT, routes.size)
    }

    @Test
    fun `imported track is downsampled before storage`() {
        val points = (0 until 5_000).map { GeoUtils.offsetMeters(start, it * 1.0, 0.0) }
        val imported = RouteConfig(
            start = points.first(),
            end = points.last(),
            kind = RouteKind.IMPORTED,
            trackPoints = points,
        )
        val routes = RouteHistoryPolicy.recordRecent(emptyList(), imported, "long", now = 1L)
        val stored = routes.single().config.trackPoints
        assertEquals(RouteHistoryPolicy.MAX_STORED_TRACK_POINTS, stored.size)
        assertEquals(points.first(), stored.first())
        assertEquals(points.last(), stored.last())
    }

    @Test
    fun `default names describe each route kind`() {
        val road = RouteHistoryPolicy.defaultName(config(0), ZhStrings)
        assertTrue(road.contains("→"))

        val roam = RouteHistoryPolicy.defaultName(
            RouteConfig(
                start = start,
                end = start,
                kind = RouteKind.ROAM,
                roamRadiusMeters = 300,
            ),
            ZhStrings,
        )
        assertEquals("漫游 · 半径 300 m", roam)

        val imported = RouteHistoryPolicy.defaultName(
            RouteConfig(start = start, end = point(1), kind = RouteKind.IMPORTED, trackPoints = listOf(start, point(1))),
            ZhStrings,
        )
        assertTrue(imported.startsWith("导入轨迹 · 2 点"))
    }

    @Test
    fun `rename rejects blank names and clamps long ones`() {
        val routes = RouteHistoryPolicy.recordRecent(emptyList(), config(0), "a", now = 1L)
        val id = routes.single().id
        assertEquals("a", RouteHistoryPolicy.rename(routes, id, "   ").single().name)
        assertEquals(40, RouteHistoryPolicy.rename(routes, id, "x".repeat(80)).single().name.length)
    }
}
