package com.gpsproxy.dev.core.geo

import com.gpsproxy.dev.core.i18n.StringsHolder
import java.net.URLEncoder
import java.util.Locale

/**
 * 外部地图链接构造（纯函数，便于单测）。
 *
 * `geo:` 是 Android 通用约定（lat,lng）；高德 Web 链接使用 `position=lng,lat`。
 * 顺序写错会让地图打开到地球另一侧，因此这里把两种顺序的差异固化在一处。
 */
object MapUriBuilder {

    fun geoUri(point: GeoPoint, label: String): String {
        val lat = format(point.lat)
        val lng = format(point.lng)
        val name = encode(label.ifBlank { StringsHolder.text { onboarding.mapMarkerLabel } })
        return "geo:$lat,$lng?q=$lat,$lng($name)"
    }

    fun amapUri(point: GeoPoint, label: String): String {
        val name = encode(label.ifBlank { StringsHolder.text { onboarding.mapMarkerLabel } })
        return "https://uri.amap.com/marker?position=${format(point.lng)},${format(point.lat)}" +
            "&name=$name&src=gpsproxy&coordinate=gaode"
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
