package com.gpsproxy.dev.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.i18n.AppLanguage
import com.gpsproxy.dev.data.settings.SettingsCodec
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.settings.DetailLevel
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.settings.NotificationDetail
import com.gpsproxy.dev.domain.settings.SettingsGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "gpsproxy_settings")

/**
 * 设置的持久化实现。
 *
 * 只做三件事：读流、按变换写、提供具名 setter。
 * 字段映射与区间钳制全部委托给 [SettingsCodec]，因此新增设置项时
 * 不需要在仓库里再维护一份字段列表。
 */
class SettingsRepository(private val context: Context) : SettingsGateway {

    private val store = context.settingsDataStore
    private val codec = SettingsCodec()

    override val settings: Flow<AppSettings> = store.data.map(codec::decode)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { preferences ->
            codec.encode(preferences, transform(codec.decode(preferences)))
        }
    }

    suspend fun setCoordSystem(value: CoordSystem) = update { it.copy(coordSystem = value) }
    suspend fun setAccuracy(value: Int) = update { it.copy(accuracyMeters = value.coerceIn(3, 50)) }
    suspend fun setDrift(enabled: Boolean) = update { it.copy(driftEnabled = enabled) }
    suspend fun setBlurEnabled(enabled: Boolean) = update { it.copy(blurEnabled = enabled) }
    suspend fun setBlurRadius(meters: Int) = update { it.copy(blurRadiusMeters = meters.coerceIn(50, 5000)) }
    suspend fun setUpdateInterval(ms: Int) = update { it.copy(updateIntervalMs = ms.coerceIn(500, 2000)) }
    suspend fun setWalkSpeed(kmh: Int) = update { it.copy(walkSpeedKmh = kmh.coerceIn(1, 20)) }
    suspend fun setReportSpeed(kmh: Int) = update { it.copy(reportSpeedKmh = kmh.coerceIn(0, 120)) }
    suspend fun setReportBearing(degrees: Int) = update { it.copy(reportBearingDeg = degrees.coerceIn(0, 359)) }
    suspend fun setAltitude(meters: Int) = update { it.copy(altitudeMeters = meters.coerceIn(0, 5000)) }
    suspend fun setAutoStop(minutes: Int) = update { it.copy(autoStopMinutes = minutes.coerceIn(0, 240)) }
    suspend fun setOverlayEnabled(enabled: Boolean) = update { it.copy(overlayEnabled = enabled) }
    suspend fun setNotificationDetail(detail: NotificationDetail) =
        update { it.copy(notificationDetail = detail) }
    suspend fun setLanguage(value: AppLanguage) = update { it.copy(language = value) }
    suspend fun setBootRestore(enabled: Boolean) = update { it.copy(bootRestore = enabled) }
    suspend fun setDetailLevel(level: DetailLevel) = update { it.copy(detailLevel = level) }
    suspend fun setInjectionChannel(channel: InjectionChannel) = update { it.copy(injectionChannel = channel) }
    suspend fun acceptDisclaimer() = update { it.copy(disclaimerAccepted = true) }

    /** 记录一次成功的引擎自检；0 值表示"从未检测"。 */
    suspend fun markSelfCheckCompleted(atMillis: Long) = update { it.copy(lastSelfCheckAt = atMillis) }

    suspend fun setTargetPackages(packages: Set<String>) = update { it.copy(targetPackages = packages) }

    override suspend fun rememberPoint(lat: Double, lng: Double, address: String) =
        update { it.copy(lastLat = lat, lastLng = lng, lastAddress = address) }
}
