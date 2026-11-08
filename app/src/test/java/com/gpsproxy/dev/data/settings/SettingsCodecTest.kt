package com.gpsproxy.dev.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.i18n.AppLanguage
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.settings.DetailLevel
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.settings.NotificationDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsCodecTest {

    private val codec = SettingsCodec()

    private val sample = AppSettings(
        coordSystem = CoordSystem.WGS84,
        accuracyMeters = 20,
        driftEnabled = false,
        blurEnabled = true,
        blurRadiusMeters = 500,
        updateIntervalMs = 2000,
        walkSpeedKmh = 8,
        reportSpeedKmh = 30,
        reportBearingDeg = 90,
        altitudeMeters = 50,
        autoStopMinutes = 10,
        overlayEnabled = false,
        notificationDetail = NotificationDetail.COMPACT,
        language = AppLanguage.EN,
        bootRestore = true,
        detailLevel = DetailLevel.VERBOSE,
        injectionChannel = InjectionChannel.OFFICIAL,
        disclaimerAccepted = true,
        lastSelfCheckAt = 1_700_000_000_000L,
        targetPackages = setOf("com.example.a", "com.example.b"),
        lastLat = 30.25,
        lastLng = 120.15,
        lastAddress = "西湖",
    )

    @Test
    fun `encode then decode is a fixed point`() {
        val preferences = mutablePreferencesOf()
        codec.encode(preferences, sample)
        assertEquals(sample, codec.decode(preferences))
    }

    @Test
    fun `defaults are used for empty preferences`() {
        assertEquals(AppSettings(), codec.decode(mutablePreferencesOf()))
    }

    @Test
    fun `unknown language falls back to system`() {
        val preferences = mutablePreferencesOf()
        preferences[stringPreferencesKey("language")] = "Klingon"
        assertEquals(AppLanguage.SYSTEM, codec.decode(preferences).language)
    }

    @Test
    fun `out of range values are clamped on decode`() {
        val preferences = mutablePreferencesOf()
        preferences[intPreferencesKey("accuracy_meters")] = 999
        preferences[intPreferencesKey("blur_radius_meters")] = 1
        preferences[intPreferencesKey("update_interval_ms")] = 5
        preferences[intPreferencesKey("walk_speed_kmh")] = 99
        preferences[intPreferencesKey("report_speed_kmh")] = 999
        preferences[intPreferencesKey("report_bearing_deg")] = 999
        preferences[intPreferencesKey("altitude_meters")] = 99_999
        preferences[intPreferencesKey("auto_stop_minutes")] = 99_999
        val decoded = codec.decode(preferences)
        assertEquals(50, decoded.accuracyMeters)
        assertEquals(50, decoded.blurRadiusMeters)
        assertEquals(500, decoded.updateIntervalMs)
        assertEquals(20, decoded.walkSpeedKmh)
        assertEquals(120, decoded.reportSpeedKmh)
        assertEquals(359, decoded.reportBearingDeg)
        assertEquals(5000, decoded.altitudeMeters)
        assertEquals(240, decoded.autoStopMinutes)
    }

    @Test
    fun `unknown enum names fall back to defaults`() {
        val preferences = mutablePreferencesOf()
        preferences[stringPreferencesKey("coord_system")] = "MARS"
        preferences[stringPreferencesKey("detail_level")] = "LOUD"
        preferences[stringPreferencesKey("injection_channel")] = "TELEPORT"
        val decoded = codec.decode(preferences)
        assertEquals(CoordSystem.GCJ02, decoded.coordSystem)
        assertEquals(DetailLevel.NORMAL, decoded.detailLevel)
        assertEquals(InjectionChannel.CONTAINER, decoded.injectionChannel)
    }

    @Test
    fun `legacy notification switch migrates to compact detail`() {
        val preferences = mutablePreferencesOf()
        preferences[booleanPreferencesKey("foreground_notification")] = false
        assertEquals(NotificationDetail.COMPACT, codec.decode(preferences).notificationDetail)
    }

    @Test
    fun `legacy engine flag is read without losing self check timestamp`() {
        val preferences = mutablePreferencesOf()
        preferences[booleanPreferencesKey("engine_initialized")] = true
        assertEquals(0L, codec.decode(preferences).lastSelfCheckAt)
    }

    @Test
    fun `encode removes legacy keys`() {
        val preferences = mutablePreferencesOf()
        preferences[booleanPreferencesKey("foreground_notification")] = false
        preferences[booleanPreferencesKey("engine_initialized")] = true
        codec.encode(preferences, sample)
        assertNull(preferences[booleanPreferencesKey("foreground_notification")])
        assertNull(preferences[booleanPreferencesKey("engine_initialized")])
    }
}
