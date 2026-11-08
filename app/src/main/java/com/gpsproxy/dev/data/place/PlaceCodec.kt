package com.gpsproxy.dev.data.place

import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.domain.place.FavoritePlace
import com.gpsproxy.dev.domain.place.HistoryEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * 收藏 / 历史 / 搜索词的 JSON 编解码。
 *
 * 从仓库里抽出的原因：
 * - 旧实现有 4 组对称的 decode/encode（收藏、历史各一对，搜索词另计），
 *   任何字段增删都要改两处，且没有测试保护；
 * - 损坏的 JSON 必须静默回落为空列表，而不是让仓库抛异常导致整页空白；
 * - 解码方向对每个字段都做 "缺省值 + 类型不匹配" 兜底。
 *
 * 这些函数是纯函数，可以用固定字符串直接断言，不需要 DataStore。
 */
object FavoriteCodec {

    const val MAX_ENTRIES = 200

    fun decode(raw: String?): List<FavoritePlace> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val coord = runCatching {
                        CoordSystem.valueOf(obj.optString("coord", CoordSystem.GCJ02.name))
                    }.getOrDefault(CoordSystem.GCJ02)
                    add(
                        FavoritePlace(
                            id = obj.optLong("id", 0L),
                            name = obj.optString("name"),
                            address = obj.optString("address"),
                            lat = obj.optDouble("lat"),
                            lng = obj.optDouble("lng"),
                            coordSystem = coord,
                            createdAt = obj.optLong("createdAt", 0L),
                            lastUsedAt = obj.optLong("lastUsedAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encode(list: List<FavoritePlace>): String {
        val array = JSONArray()
        list.forEach { place ->
            array.put(
                JSONObject()
                    .put("id", place.id)
                    .put("name", place.name)
                    .put("address", place.address)
                    .put("lat", place.lat)
                    .put("lng", place.lng)
                    .put("coord", place.coordSystem.name)
                    .put("createdAt", place.createdAt)
                    .put("lastUsedAt", place.lastUsedAt)
            )
        }
        return array.toString()
    }
}

object HistoryCodec {

    const val MAX_ENTRIES = 100

    fun decode(raw: String?): List<HistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    add(
                        HistoryEntry(
                            id = obj.optLong("id", 0L),
                            name = obj.optString("name"),
                            address = obj.optString("address"),
                            lat = obj.optDouble("lat"),
                            lng = obj.optDouble("lng"),
                            usedAt = obj.optLong("usedAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encode(list: List<HistoryEntry>): String {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("address", entry.address)
                    .put("lat", entry.lat)
                    .put("lng", entry.lng)
                    .put("usedAt", entry.usedAt)
            )
        }
        return array.toString()
    }
}

object SearchTermsCodec {

    const val MAX_ENTRIES = 20

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encode(list: List<String>): String {
        val array = JSONArray()
        list.forEach(array::put)
        return array.toString()
    }
}
