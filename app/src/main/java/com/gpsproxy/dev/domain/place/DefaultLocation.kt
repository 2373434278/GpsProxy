package com.gpsproxy.dev.domain.place

import com.gpsproxy.dev.core.geo.GeoPoint

/**
 * 默认选点与地址文案。
 *
 * 单独抽出是因为它同时被 UI 初始状态、诊断报告与开机恢复引用；
 * 散落在 ViewModel 的 companion 里会让"默认位置"变成一个 UI 细节，
 * 而不是产品配置。
 */
object DefaultLocation {

    val POINT: GeoPoint = GeoPoint(30.250213, 120.148832)

    const val ADDRESS: String = "西湖风景名胜区"
}
