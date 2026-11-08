package com.gpsproxy.dev.ui.session

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.domain.simulation.route.SavedRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDraftStoreTest {

    private val start = GeoPoint(30.250213, 120.148832)
    private val end = GeoPoint(30.260000, 120.160000)

    private fun store(): RouteDraftStore = RouteDraftStore().apply {
        initializeStart(start, "西湖")
        setEnd(end, "终点")
    }

    @Test
    fun `initialize start never overwrites an existing start`() {
        val store = RouteDraftStore()
        store.initializeStart(start, "a")
        store.initializeStart(end, "b")
        assertEquals(start, store.draft.value.start)
        assertEquals("a", store.draft.value.startAddress)
    }

    @Test
    fun `waypoints dedupe and cap at eight`() {
        val store = store()
        repeat(12) { index ->
            store.addWaypoint(GeoUtils.offsetMeters(start, 100.0 + index * 50.0, 0.0), "w$index")
        }
        assertEquals(RouteDraftStore.MAX_WAYPOINTS, store.draft.value.waypoints.size)

        store.addWaypoint(store.draft.value.waypoints.first().point, "duplicate")
        assertEquals(RouteDraftStore.MAX_WAYPOINTS, store.draft.value.waypoints.size)

        store.addWaypoint(start, "same-as-start")
        assertEquals(RouteDraftStore.MAX_WAYPOINTS, store.draft.value.waypoints.size)
    }

    @Test
    fun `move and remove waypoints keep order`() {
        val store = store()
        val a = GeoUtils.offsetMeters(start, 100.0, 0.0)
        val b = GeoUtils.offsetMeters(start, 200.0, 0.0)
        store.addWaypoint(a, "a")
        store.addWaypoint(b, "b")

        store.moveWaypoint(0, 1)
        assertEquals(listOf(b, a), store.draft.value.waypoints.map { it.point })

        store.removeWaypoint(0)
        assertEquals(listOf(a), store.draft.value.waypoints.map { it.point })

        store.removeWaypoint(99)
        assertEquals(1, store.draft.value.waypoints.size)
    }

    @Test
    fun `point route config includes waypoints and mode`() {
        val store = store()
        val waypoint = GeoUtils.offsetMeters(start, 100.0, 50.0)
        store.addWaypoint(waypoint, "w")
        store.setSpeed(45)

        val config = requireNotNull(store.draft.value.toConfig())
        assertEquals(RouteKind.POINT_TO_POINT, config.kind)
        assertEquals(start, config.start)
        assertEquals(end, config.end)
        assertEquals(listOf(waypoint), config.waypoints)
        assertEquals(45, config.speedKmh)
    }

    @Test
    fun `roam config forces a loop from the start anchor`() {
        val store = store()
        store.setKind(RouteKind.ROAM)
        store.setRoamRadius(1234)
        store.setSpeed(8)

        val config = requireNotNull(store.draft.value.toConfig())
        assertEquals(RouteKind.ROAM, config.kind)
        assertEquals(start, config.start)
        assertEquals(start, config.end)
        assertTrue(config.loop)
        assertFalse(config.roundTrip)
        assertEquals(1234, config.roamRadiusMeters)
    }

    @Test
    fun `imported track config uses first and last points`() {
        val store = store()
        val points = (0..5).map { GeoUtils.offsetMeters(start, it * 30.0, 0.0) }
        store.setImportedTrack(points, "gpx")

        val draft = store.draft.value
        assertTrue(draft.isComplete)
        assertEquals(RouteKind.IMPORTED, draft.kind)
        assertEquals(6, draft.trackPoints.size)

        val config = requireNotNull(draft.toConfig())
        assertEquals(RouteKind.IMPORTED, config.kind)
        assertEquals(points.first(), config.start)
        assertEquals(points.last(), config.end)
        assertEquals(points, config.trackPoints)
    }

    @Test
    fun `loading a saved route restores all editable fields`() {
        val store = RouteDraftStore()
        val config = RouteConfig(
            start = start,
            end = end,
            speedKmh = 18,
            loop = true,
            waypoints = listOf(GeoUtils.offsetMeters(start, 100.0, 0.0)),
        )
        val saved = SavedRoute(
            id = 1L,
            name = "我的路线",
            config = config,
            favorite = true,
            createdAt = 1L,
            lastUsedAt = 2L,
        )

        store.loadSavedRoute(saved)

        val draft = store.draft.value
        assertEquals(start, draft.start)
        assertEquals(end, draft.end)
        assertEquals(18, draft.speedKmh)
        assertTrue(draft.loop)
        assertEquals(1, draft.waypoints.size)
        assertEquals("我的路线", draft.trackName)
    }

    @Test
    fun `incomplete drafts yield no config`() {
        assertNull(RouteDraftStore().draft.value.toConfig())
        assertFalse(RouteDraftStore().draft.value.isComplete)

        val imported = RouteDraftStore()
        imported.setKind(RouteKind.IMPORTED)
        assertNull(imported.draft.value.toConfig())
        assertFalse(imported.draft.value.isComplete)
    }
}
