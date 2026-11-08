package com.gpsproxy.dev.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.domain.session.RecoverableSession
import com.gpsproxy.dev.domain.session.SessionRecorder
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "gpsproxy_session")

/**
 * 会话恢复信息的持久化。
 *
 * 独立于设置文件的原因：模拟运行期间会频繁写设置（选点、模糊半径等），
 * 把"上次是否在运行"混进同一个 DataStore 会让每次坐标更新都可能触发
 * 全量设置重写；单独文件也能在升级时独立清理。
 */
class SessionStore(private val context: Context) {

    val session: Flow<RecoverableSession?> = context.sessionDataStore.data.map { preferences ->
        if (preferences[KEY_ACTIVE] != true) return@map null
        RecoverableSession(
            wasActive = true,
            mode = preferences[KEY_MODE]?.let { name ->
                SimMode.entries.firstOrNull { it.name == name }
            },
            point = preferences[KEY_LAT]?.let { lat ->
                preferences[KEY_LNG]?.let { lng -> GeoPoint(lat, lng) }
            },
            address = preferences[KEY_ADDRESS] ?: "",
            startedAt = preferences[KEY_STARTED_AT] ?: 0L,
        )
    }

    suspend fun last(): RecoverableSession? = session.first()

    suspend fun record(session: RecoverableSession) {
        context.sessionDataStore.edit { preferences ->
            preferences[KEY_ACTIVE] = session.wasActive
            preferences[KEY_MODE] = session.mode?.name ?: ""
            session.point?.let {
                preferences[KEY_LAT] = it.lat
                preferences[KEY_LNG] = it.lng
            } ?: run {
                preferences.remove(KEY_LAT)
                preferences.remove(KEY_LNG)
            }
            preferences[KEY_ADDRESS] = session.address
            preferences[KEY_STARTED_AT] = session.startedAt
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { preferences ->
            preferences[KEY_ACTIVE] = false
            preferences.remove(KEY_MODE)
            preferences.remove(KEY_LAT)
            preferences.remove(KEY_LNG)
            preferences.remove(KEY_ADDRESS)
            preferences.remove(KEY_STARTED_AT)
        }
    }

    private companion object {
        val KEY_ACTIVE = booleanPreferencesKey("session_active")
        val KEY_MODE = stringPreferencesKey("session_mode")
        val KEY_LAT = doublePreferencesKey("session_lat")
        val KEY_LNG = doublePreferencesKey("session_lng")
        val KEY_ADDRESS = stringPreferencesKey("session_address")
        val KEY_STARTED_AT = longPreferencesKey("session_started_at")
    }
}

/** 把引擎的启动/停止事件翻译成持久化写入。 */
class DataStoreSessionRecorder(private val store: SessionStore) : SessionRecorder {

    override suspend fun onStarted(state: SimulationState) {
        store.record(
            RecoverableSession(
                wasActive = true,
                mode = state.mode,
                point = state.point,
                address = state.address,
                startedAt = state.startedAt,
            ),
        )
    }

    override suspend fun onStopped() {
        store.clear()
    }
}
