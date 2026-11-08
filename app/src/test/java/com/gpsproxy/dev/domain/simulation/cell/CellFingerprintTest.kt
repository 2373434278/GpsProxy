package com.gpsproxy.dev.domain.simulation.cell

import com.gpsproxy.dev.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CellFingerprintTest {

    private val point = GeoPoint(30.250213, 120.148832)

    @Test
    fun `same point always yields the same cell`() {
        assertEquals(CellFingerprint.derive(point, 10), CellFingerprint.derive(point, 10))
    }

    @Test
    fun `different points yield different cells`() {
        val other = GeoPoint(39.908722, 116.397499)
        assertTrue(CellFingerprint.derive(point, 10) != CellFingerprint.derive(other, 10))
    }

    @Test
    fun `cell fields stay inside plausible ranges`() {
        val cell = CellFingerprint.derive(point, 12)
        assertEquals(460, cell.mcc)
        assertTrue("mnc=${cell.mnc}", cell.mnc in intArrayOf(0, 2, 7).toList())
        assertTrue("lac=${cell.lac}", cell.lac in 1000..61_000)
        assertTrue("cid=${cell.cid}", cell.cid in 1..268_435_455)
        assertTrue("dbm=${cell.signalStrengthDbm}", cell.signalStrengthDbm in -113..-65)
        assertTrue(cell.signalLevel in 0..4)
        assertTrue(cell.registered)
    }

    @Test
    fun `network type follows satellite count`() {
        assertEquals(CellNetworkType.UMTS, CellFingerprint.derive(point, 4).networkType)
        assertEquals(CellNetworkType.LTE, CellFingerprint.derive(point, 9).networkType)
    }

    @Test
    fun `signal level thresholds are stable`() {
        assertEquals(0, CellFingerprint.signalLevel(0))
        assertEquals(1, CellFingerprint.signalLevel(3))
        assertEquals(2, CellFingerprint.signalLevel(6))
        assertEquals(3, CellFingerprint.signalLevel(10))
        assertEquals(4, CellFingerprint.signalLevel(14))
    }
}
