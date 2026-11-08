package com.gpsproxy.dev.data.route

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.route.RouteHistoryPolicy
import com.gpsproxy.dev.domain.simulation.route.RouteSignature
import com.gpsproxy.dev.domain.simulation.route.RouteStore
import com.gpsproxy.dev.domain.simulation.route.RouteUsageRecorder
import com.gpsproxy.dev.domain.simulation.route.SavedRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.routesDataStore by preferencesDataStore(name = "gpsproxy_routes")

/**
 * 路线库的持久化实现。
 *
 * 同时实现两个端口：
 * - [RouteStore]：用户显式操作（收藏/重命名/删除/触达）；
 * - [RouteUsageRecorder]：引擎在轨迹首帧成功后自动记录最近路线。
 *
 * 单独的 DataStore 文件避免与设置/点收藏争用同一把事务锁。
 */
class RoutesRepository(private val context: Context) : RouteStore, RouteUsageRecorder {

    private val store = context.routesDataStore

    override val routes: Flow<List<SavedRoute>> =
        store.data.map { SavedRouteCodec.decode(it[KEY_ROUTES]) }

    override suspend fun recordRecent(config: RouteConfig, name: String) {
        update { routes ->
            RouteHistoryPolicy.recordRecent(
                existing = routes,
                config = config,
                name = name,
                now = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        update { routes -> RouteHistoryPolicy.setFavorite(routes, id, favorite) }
    }

    override suspend fun saveFavorite(config: RouteConfig, name: String): Long {
        var savedId = 0L
        store.edit { preferences ->
            val now = System.currentTimeMillis()
            val sanitized = RouteHistoryPolicy.sanitize(config)
            val recorded = RouteHistoryPolicy.recordRecent(
                existing = SavedRouteCodec.decode(preferences[KEY_ROUTES]),
                config = sanitized,
                name = name,
                now = now,
            )
            val signature = RouteSignature.of(sanitized)
            val saved = recorded.firstOrNull { RouteSignature.of(it.config) == signature }
            val next = if (saved != null) {
                savedId = saved.id
                RouteHistoryPolicy.setFavorite(recorded, saved.id, true)
            } else {
                // 路线已被最近列表裁剪时，直接补一条收藏，避免误收藏其它路线。
                val created = SavedRoute(
                    id = now,
                    name = name.ifBlank { RouteHistoryPolicy.defaultName(sanitized) },
                    config = sanitized,
                    favorite = true,
                    createdAt = now,
                    lastUsedAt = now,
                )
                savedId = created.id
                RouteHistoryPolicy.setFavorite(recorded + created, created.id, true)
            }
            preferences[KEY_ROUTES] = SavedRouteCodec.encode(next)
        }
        return savedId
    }

    override suspend fun rename(id: Long, name: String) {
        update { routes -> RouteHistoryPolicy.rename(routes, id, name) }
    }

    override suspend fun remove(id: Long) {
        update { routes -> RouteHistoryPolicy.remove(routes, id) }
    }

    override suspend fun touch(id: Long) {
        update { routes -> RouteHistoryPolicy.touch(routes, id, System.currentTimeMillis()) }
    }

    override suspend fun onRouteStarted(config: RouteConfig) {
        recordRecent(config, RouteHistoryPolicy.defaultName(config))
    }

    private suspend fun update(transform: (List<SavedRoute>) -> List<SavedRoute>) {
        store.edit { preferences ->
            val current = SavedRouteCodec.decode(preferences[KEY_ROUTES])
            preferences[KEY_ROUTES] = SavedRouteCodec.encode(transform(current))
        }
    }

    private companion object {
        val KEY_ROUTES = stringPreferencesKey("routes_json")
    }
}
