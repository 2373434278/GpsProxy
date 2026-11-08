package com.gpsproxy.dev.data.place

import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.domain.place.FavoritePlace
import com.gpsproxy.dev.domain.place.HistoryEntry
import com.gpsproxy.dev.domain.place.PlaceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCodecTest {

    private val favorite = FavoritePlace(
        id = 7L,
        name = "西湖",
        address = "西湖风景名胜区",
        lat = 30.250213,
        lng = 120.148832,
        coordSystem = CoordSystem.WGS84,
        createdAt = 100L,
        lastUsedAt = 200L,
    )

    @Test
    fun `favorite round trip preserves every field`() {
        val decoded = FavoriteCodec.decode(FavoriteCodec.encode(listOf(favorite)))
        assertEquals(listOf(favorite), decoded)
    }

    @Test
    fun `history round trip preserves every field`() {
        val entry = HistoryEntry(1L, "西湖", "西湖风景名胜区", 30.25, 120.15, 300L)
        assertEquals(listOf(entry), HistoryCodec.decode(HistoryCodec.encode(listOf(entry))))
    }

    @Test
    fun `search terms round trip filters blanks`() {
        val encoded = SearchTermsCodec.encode(listOf("西湖", " ", "断桥"))
        assertEquals(listOf("西湖", "断桥"), SearchTermsCodec.decode(encoded))
    }

    @Test
    fun `corrupt payload falls back to empty lists`() {
        assertTrue(FavoriteCodec.decode("{not-json").isEmpty())
        assertTrue(HistoryCodec.decode("[bad").isEmpty())
        assertTrue(SearchTermsCodec.decode("nope").isEmpty())
        assertTrue(FavoriteCodec.decode(null).isEmpty())
    }

    @Test
    fun `unknown coordinate system falls back to gcj02`() {
        val raw = """[{"id":1,"name":"a","address":"b","lat":1.0,"lng":2.0,"coord":"MARS"}]"""
        assertEquals(CoordSystem.GCJ02, FavoriteCodec.decode(raw).first().coordSystem)
    }

    @Test
    fun `favorite identity matches by name or coordinate`() {
        assertTrue(PlaceIdentity.sameFavorite(favorite, favorite.copy(id = 9L, lat = 31.0)))
        assertTrue(PlaceIdentity.sameFavorite(favorite, favorite.copy(name = "other")))
        assertFalse(
            PlaceIdentity.sameFavorite(
                favorite,
                favorite.copy(id = 9L, name = "other", lat = 31.0, lng = 121.0),
            )
        )
    }

    @Test
    fun `history identity requires both name and coordinate`() {
        val entry = HistoryEntry(1L, "西湖", "addr", 30.0, 120.0, 0L)
        assertFalse(PlaceIdentity.sameHistory(entry, entry.copy(name = "other")))
        assertFalse(PlaceIdentity.sameHistory(entry, entry.copy(lat = 31.0)))
        assertTrue(PlaceIdentity.sameHistory(entry, entry.copy(id = 2L)))
    }
}
