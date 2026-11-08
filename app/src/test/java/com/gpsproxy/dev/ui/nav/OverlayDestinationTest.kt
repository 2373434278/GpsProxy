package com.gpsproxy.dev.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayDestinationTest {

    @Test
    fun `known destinations parse`() {
        assertEquals(OverlayDestination.FAVORITES, OverlayDestination.parse("favorites"))
        assertEquals(OverlayDestination.PICK, OverlayDestination.parse("pick"))
        assertEquals(OverlayDestination.APPS, OverlayDestination.parse("apps"))
        assertEquals(OverlayDestination.ROUTE, OverlayDestination.parse("route"))
    }

    @Test
    fun `unknown or null falls back to UNKNOWN`() {
        assertEquals(OverlayDestination.UNKNOWN, OverlayDestination.parse("nope"))
        assertEquals(OverlayDestination.UNKNOWN, OverlayDestination.parse(null))
        assertEquals(OverlayDestination.UNKNOWN, OverlayDestination.parse(""))
    }
}
