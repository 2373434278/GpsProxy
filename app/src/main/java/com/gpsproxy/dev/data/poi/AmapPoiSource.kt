package com.gpsproxy.dev.data.poi

import com.gpsproxy.dev.core.i18n.StringsHolder

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.data.net.HttpTextClient
import com.gpsproxy.dev.domain.place.Poi
import com.gpsproxy.dev.domain.place.PoiCategory
import com.gpsproxy.dev.domain.place.PoiQuery
import com.gpsproxy.dev.domain.place.PoiSearchResult
import com.gpsproxy.dev.domain.place.PoiSearchSource
import com.gpsproxy.dev.domain.place.PoiSource
import org.json.JSONObject
import java.io.IOException

/**
 * 高德 Web 服务数据源（POI 搜索 + 逆地理编码）。
 *
 * 只做协议适配：参数拼装、JSON 解析、分类推断与错误转换。
 * 超时/重试由 [HttpTextClient] 负责；失败后的回退由 `FallbackPoiSource` 负责，
 * 因此这里失败时直接抛 [IOException] 即可，不需要自己拼警告文案。
 */
class AmapPoiSource(
    private val client: HttpTextClient,
    private val key: String,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : PoiSearchSource {

    override val hasLiveSource: Boolean = key.isNotBlank()

    override suspend fun search(query: PoiQuery): PoiSearchResult {
        val json = if (query.nearby && query.center != null) {
            searchAround(query)
        } else {
            searchText(query)
        }
        return parsePois(json)
    }

    override suspend fun reverseGeocode(point: GeoPoint): String? {
        val json = client.get(
            REVERSE_URL,
            linkedMapOf(
                "key" to key,
                "location" to "${point.lng},${point.lat}",
                "extensions" to "base",
            ),
        )
        val root = JSONObject(json)
        if (root.optString("status") != "1") return null
        return root.optJSONObject("regeocode")?.optString("formatted_address")
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun searchText(query: PoiQuery): String {
        val params = linkedMapOf(
            "key" to key,
            "keywords" to query.query,
            "offset" to pageSize.toString(),
            "page" to (query.page + 1).toString(),
            "extensions" to "base",
        )
        if (query.category != PoiCategory.ALL) params["types"] = query.category.amapType
        return client.get(TEXT_URL, params)
    }

    private suspend fun searchAround(query: PoiQuery): String {
        val center = requireNotNull(query.center)
        val params = linkedMapOf(
            "key" to key,
            "location" to "${center.lng},${center.lat}",
            "radius" to query.radiusMeters.toString(),
            "offset" to pageSize.toString(),
            "page" to (query.page + 1).toString(),
            "extensions" to "base",
        )
        if (query.query.isNotBlank()) params["keywords"] = query.query
        if (query.category != PoiCategory.ALL) params["types"] = query.category.amapType
        return client.get(AROUND_URL, params)
    }

    private fun parsePois(json: String): PoiSearchResult {
        val root = JSONObject(json)
        if (root.optString("status") != "1") {
            throw IOException(root.optString("info", "amap error"))
        }
        val array = root.optJSONArray("pois")
            ?: return PoiSearchResult(items = emptyList(), source = PoiSource.AMAP)
        val items = buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val parts = obj.optString("location").split(",")
                if (parts.size != 2) continue
                val lng = parts[0].toDoubleOrNull() ?: continue
                val lat = parts[1].toDoubleOrNull() ?: continue
                val address = buildString {
                    append(obj.optString("pname"))
                    append(obj.optString("cityname"))
                    append(obj.optString("adname"))
                    append(obj.optString("address"))
                }.ifBlank { obj.optString("address") }
                add(
                    Poi(
                        name = obj.optString("name").ifBlank {
                            StringsHolder.text { place.unnamedPlace }
                        },
                        address = address,
                        lat = lat,
                        lng = lng,
                        category = inferCategory(obj.optString("type")),
                        city = obj.optString("cityname"),
                    )
                )
            }
        }
        return PoiSearchResult(
            items = items,
            source = PoiSource.AMAP,
            hasMore = items.size >= pageSize,
        )
    }

    private fun inferCategory(type: String): PoiCategory = when {
        type.contains("景点") || type.contains("公园") || type.contains("风景") -> PoiCategory.SCENIC
        type.contains("餐") || type.contains("美食") -> PoiCategory.FOOD
        type.contains("地铁") || type.contains("公交") || type.contains("火车站") ||
            type.contains("机场") -> PoiCategory.TRANSIT
        type.contains("购物") || type.contains("商场") -> PoiCategory.MALL
        type.contains("酒店") || type.contains("宾馆") -> PoiCategory.HOTEL
        type.contains("医院") || type.contains("诊所") -> PoiCategory.HOSPITAL
        else -> PoiCategory.OTHER
    }

    private companion object {
        const val TEXT_URL = "https://restapi.amap.com/v3/place/text"
        const val AROUND_URL = "https://restapi.amap.com/v3/place/around"
        const val REVERSE_URL = "https://restapi.amap.com/v3/geocode/regeo"
        const val DEFAULT_PAGE_SIZE = 20
    }
}
