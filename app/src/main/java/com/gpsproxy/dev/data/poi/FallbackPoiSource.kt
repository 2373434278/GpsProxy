package com.gpsproxy.dev.data.poi

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.domain.place.PoiQuery
import com.gpsproxy.dev.domain.place.PoiSearchResult
import com.gpsproxy.dev.domain.place.PoiSearchSource

/**
 * 网络优先、本地兜底。
 *
 * 这是旧 `PoiRepository` 里两处 `if (amapKey.isNotEmpty())` 分支的显式化版本：
 * - 没有 Key 时 `primary == null`，直接走本地，结果不带警告（本来就是预期行为）；
 * - 有 Key 但失败时回退本地，并附带明确警告，UI 据此提示"已回退内置示例数据"。
 *
 * 回退只发生在查询失败，不会把网络数据与本地数据混进同一页，
 * 避免"来源"标签与实际内容不一致。
 */
class FallbackPoiSource(
    private val primary: PoiSearchSource?,
    private val fallback: PoiSearchSource,
    private val fallbackWarning: String? = null,
) : PoiSearchSource {

    override val hasLiveSource: Boolean
        get() = primary?.hasLiveSource == true

    override suspend fun search(query: PoiQuery): PoiSearchResult {
        val live = primary ?: return fallback.search(query)
        val result = runCatching { live.search(query) }.getOrNull()
            ?: return fallback.search(query).copy(
                warning = fallbackWarning ?: StringsHolder.text { place.fallbackWarning },
            )
        return result
    }

    override suspend fun reverseGeocode(point: GeoPoint): String? {
        val live = primary ?: return fallback.reverseGeocode(point)
        val resolved = runCatching { live.reverseGeocode(point) }.getOrNull()
        if (!resolved.isNullOrBlank()) return resolved
        return fallback.reverseGeocode(point)
    }
}

/**
 * 内存缓存装饰器（Cache-Aside）。
 *
 * 搜索页每输入一个字符都可能触发查询（含 300ms 防抖），翻页回看同一个关键词
 * 也很常见。加一层短 TTL 缓存可以避免重复请求，同时不引入持久化与失效复杂度。
 *
 * 只缓存**成功且非回退**的结果：回退结果缓存 60 秒会让网络恢复后仍显示"网络异常"。
 */
class CachingPoiSource(
    private val delegate: PoiSearchSource,
    private val clock: com.gpsproxy.dev.core.time.Clock,
    private val ttlMillis: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : PoiSearchSource {

    private data class Entry(val result: PoiSearchResult, val storedAt: Long)

    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxEntries
    }

    override val hasLiveSource: Boolean
        get() = delegate.hasLiveSource

    override suspend fun search(query: PoiQuery): PoiSearchResult {
        val key = cacheKey(query)
        synchronized(cache) {
            cache[key]?.takeIf { clock.wallMillis() - it.storedAt <= ttlMillis }?.let { return it.result }
        }
        val result = delegate.search(query)
        if (result.warning == null) {
            synchronized(cache) {
                cache[key] = Entry(result, clock.wallMillis())
            }
        }
        return result
    }

    override suspend fun reverseGeocode(point: GeoPoint): String? = delegate.reverseGeocode(point)

    private fun cacheKey(query: PoiQuery): String = buildString {
        append(query.query.trim())
        append('|').append(query.category.name)
        append('|').append(query.center?.formatted() ?: "-")
        append('|').append(query.nearby)
        append('|').append(query.radiusMeters)
        append('|').append(query.page)
    }

    private companion object {
        const val DEFAULT_TTL_MS = 60_000L
        const val DEFAULT_MAX_ENTRIES = 32
    }
}
