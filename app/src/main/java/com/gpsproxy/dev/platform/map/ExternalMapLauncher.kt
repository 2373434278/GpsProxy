package com.gpsproxy.dev.platform.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.MapUriBuilder
import com.gpsproxy.dev.core.log.LogBus

/**
 * 用外部地图应用打开一个坐标。
 *
 * 先尝试 Android 通用的 `geo:` intent；设备上没有处理者时回退高德 Web 链接
 * （由浏览器或高德 App 接管）。两条都失败时返回 false，由 UI 提示。
 */
class ExternalMapLauncher(private val context: Context) {

    fun open(point: GeoPoint, label: String): Boolean {
        if (tryOpen(MapUriBuilder.geoUri(point, label))) return true
        if (tryOpen(MapUriBuilder.amapUri(point, label))) return true
        LogBus.warn("no external map app available")
        return false
    }

    private fun tryOpen(uri: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrElse { false }
}
