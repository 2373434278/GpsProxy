package com.gpsproxy.dev.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.i18n.AppLanguage
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.settings.DetailLevel
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.settings.NotificationDetail

/**
 * 设置项的全部 DataStore 键。
 *
 * 键名属于**持久化契约**：改名等于丢用户数据，必须保留旧键读取或迁移，
 * 因此集中在一个对象里，并在 [SettingsCodec] 中成对维护读写。
 */
object SettingsKeys {
    val coord = stringPreferencesKey("coord_system")
    val accuracy = intPreferencesKey("accuracy_meters")
    val drift = booleanPreferencesKey("drift_enabled")
    val blur = booleanPreferencesKey("blur_enabled")
    val blurRadius = intPreferencesKey("blur_radius_meters")
    val interval = intPreferencesKey("update_interval_ms")
    val walkSpeed = intPreferencesKey("walk_speed_kmh")
    val reportSpeed = intPreferencesKey("report_speed_kmh")
    val reportBearing = intPreferencesKey("report_bearing_deg")
    val altitude = intPreferencesKey("altitude_meters")
    val autoStop = intPreferencesKey("auto_stop_minutes")
    val overlay = booleanPreferencesKey("overlay_enabled")
    val notificationDetail = stringPreferencesKey("notification_detail")
    val language = stringPreferencesKey("language")
    val bootRestore = booleanPreferencesKey("boot_restore")
    val detail = stringPreferencesKey("detail_level")
    val channel = stringPreferencesKey("injection_channel")
    val disclaimer = booleanPreferencesKey("disclaimer_accepted")
    val lastSelfCheckAt = longPreferencesKey("last_self_check_at")
    val targets = stringSetPreferencesKey("target_packages")
    val lastLat = doublePreferencesKey("last_lat")
    val lastLng = doublePreferencesKey("last_lng")
    val lastAddress = stringPreferencesKey("last_address")

    // ---- 历史键：只读，用于无损迁移 ----
    /** 旧版"前台通知开关"，语义已细化为 [notificationDetail]。 */
    val legacyForegroundNotification = booleanPreferencesKey("foreground_notification")
    /** 旧版"引擎已初始化"布尔值，已被"上次自检时间"取代。 */
    val legacyEngineInitialized = booleanPreferencesKey("engine_initialized")
}

/**
 * 设置与 Preferences 之间的唯一映射。
 *
 * 旧实现有 `Preferences.toSettings()` 与 `MutablePreferences.write()` 两套
 * 并行映射、各 22 条赋值，新增字段漏改一侧就会静默丢配置。现在读写都在这里，
 * 单测用 `encode → decode` 定点性质即可发现遗漏。
 *
 * 解码时统一做区间钳制与脏数据回落：配置可能来自旧版本或被手工篡改，
 * 不应该让一次异常读取把整个模拟循环带到非法状态。
 */
class SettingsCodec {

    fun decode(preferences: Preferences): AppSettings {
        val coord = preferences[SettingsKeys.coord].toEnum(CoordSystem.entries, CoordSystem.GCJ02)
        val detail = preferences[SettingsKeys.detail].toEnum(DetailLevel.entries, DetailLevel.NORMAL)
        val channel = preferences[SettingsKeys.channel]
            .toEnum(InjectionChannel.entries, InjectionChannel.CONTAINER)
        val notification = preferences[SettingsKeys.notificationDetail]
            .toEnum(NotificationDetail.entries, defaultNotificationDetail(preferences))
        val language = preferences[SettingsKeys.language]
            .toEnum(AppLanguage.entries, AppLanguage.SYSTEM)

        return AppSettings(
            coordSystem = coord,
            accuracyMeters = (preferences[SettingsKeys.accuracy] ?: DEFAULT_ACCURACY).coerceIn(3, 50),
            driftEnabled = preferences[SettingsKeys.drift] ?: true,
            blurEnabled = preferences[SettingsKeys.blur] ?: false,
            blurRadiusMeters = (preferences[SettingsKeys.blurRadius] ?: DEFAULT_BLUR_RADIUS).coerceIn(50, 5000),
            updateIntervalMs = (preferences[SettingsKeys.interval] ?: DEFAULT_INTERVAL).coerceIn(500, 2000),
            walkSpeedKmh = (preferences[SettingsKeys.walkSpeed] ?: DEFAULT_WALK_SPEED).coerceIn(1, 20),
            reportSpeedKmh = (preferences[SettingsKeys.reportSpeed] ?: 0).coerceIn(0, 120),
            reportBearingDeg = (preferences[SettingsKeys.reportBearing] ?: 0).coerceIn(0, 359),
            altitudeMeters = (preferences[SettingsKeys.altitude] ?: DEFAULT_ALTITUDE).coerceIn(0, 5000),
            autoStopMinutes = (preferences[SettingsKeys.autoStop] ?: 0).coerceIn(0, 240),
            overlayEnabled = preferences[SettingsKeys.overlay] ?: true,
            notificationDetail = notification,
            language = language,
            bootRestore = preferences[SettingsKeys.bootRestore] ?: false,
            detailLevel = detail,
            injectionChannel = channel,
            disclaimerAccepted = preferences[SettingsKeys.disclaimer] ?: false,
            lastSelfCheckAt = preferences[SettingsKeys.lastSelfCheckAt]
                ?: if (preferences[SettingsKeys.legacyEngineInitialized] == true) 0L else 0L,
            targetPackages = preferences[SettingsKeys.targets] ?: emptySet(),
            lastLat = preferences[SettingsKeys.lastLat],
            lastLng = preferences[SettingsKeys.lastLng],
            lastAddress = preferences[SettingsKeys.lastAddress] ?: "",
        )
    }

    fun encode(target: MutablePreferences, value: AppSettings) {
        target[SettingsKeys.coord] = value.coordSystem.name
        target[SettingsKeys.accuracy] = value.accuracyMeters.coerceIn(3, 50)
        target[SettingsKeys.drift] = value.driftEnabled
        target[SettingsKeys.blur] = value.blurEnabled
        target[SettingsKeys.blurRadius] = value.blurRadiusMeters.coerceIn(50, 5000)
        target[SettingsKeys.interval] = value.updateIntervalMs.coerceIn(500, 2000)
        target[SettingsKeys.walkSpeed] = value.walkSpeedKmh.coerceIn(1, 20)
        target[SettingsKeys.reportSpeed] = value.reportSpeedKmh.coerceIn(0, 120)
        target[SettingsKeys.reportBearing] = value.reportBearingDeg.coerceIn(0, 359)
        target[SettingsKeys.altitude] = value.altitudeMeters.coerceIn(0, 5000)
        target[SettingsKeys.autoStop] = value.autoStopMinutes.coerceIn(0, 240)
        target[SettingsKeys.overlay] = value.overlayEnabled
        target[SettingsKeys.notificationDetail] = value.notificationDetail.name
        target[SettingsKeys.language] = value.language.name
        target[SettingsKeys.bootRestore] = value.bootRestore
        target[SettingsKeys.detail] = value.detailLevel.name
        target[SettingsKeys.channel] = value.injectionChannel.name
        target[SettingsKeys.disclaimer] = value.disclaimerAccepted
        target[SettingsKeys.lastSelfCheckAt] = value.lastSelfCheckAt
        target[SettingsKeys.targets] = value.targetPackages
        value.lastLat?.let { target[SettingsKeys.lastLat] = it } ?: target.remove(SettingsKeys.lastLat)
        value.lastLng?.let { target[SettingsKeys.lastLng] = it } ?: target.remove(SettingsKeys.lastLng)
        target[SettingsKeys.lastAddress] = value.lastAddress
        // 写入新结构后清理历史键，避免两套语义长期并存。
        target.remove(SettingsKeys.legacyForegroundNotification)
        target.remove(SettingsKeys.legacyEngineInitialized)
    }

    private fun defaultNotificationDetail(preferences: Preferences): NotificationDetail =
        when (preferences[SettingsKeys.legacyForegroundNotification]) {
            false -> NotificationDetail.COMPACT
            else -> NotificationDetail.DETAILED
        }

    private inline fun <reified T : Enum<T>> String?.toEnum(entries: List<T>, fallback: T): T {
        if (this == null) return fallback
        return entries.firstOrNull { it.name == this } ?: fallback
    }

    private companion object {
        const val DEFAULT_ACCURACY = 5
        const val DEFAULT_BLUR_RADIUS = 200
        const val DEFAULT_INTERVAL = 1000
        const val DEFAULT_WALK_SPEED = 5
        const val DEFAULT_ALTITUDE = 12
    }
}
