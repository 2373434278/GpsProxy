package com.gpsproxy.dev.ui.session

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.domain.place.DefaultLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨页面的选点草稿。
 *
 * 旧实现把这些字段放在唯一的 ViewModel 里，所有屏幕共享同一个实例才能传递
 * "正在选的点"。拆分成多个按屏 ViewModel 后，这类**跨屏会话状态**必须显式
 * 找一个所有者：由组合根创建一个单例，相关 ViewModel 注入同一实例。
 *
 * 只保存 UI 草稿（尚未生效的选点），已生效的选点属于 `SimulationEngine`。
 */
class PlaceSelectionStore {

    private val _point = MutableStateFlow<GeoPoint?>(null)
    val point: StateFlow<GeoPoint?> = _point.asStateFlow()

    private val _address = MutableStateFlow("")
    val address: StateFlow<String> = _address.asStateFlow()

    private val _reverseAddress = MutableStateFlow<String?>(null)
    val reverseAddress: StateFlow<String?> = _reverseAddress.asStateFlow()

    /** 搜索/周边查询使用的中心点。 */
    private val _mapCenter = MutableStateFlow(DefaultLocation.POINT)
    val mapCenter: StateFlow<GeoPoint> = _mapCenter.asStateFlow()

    fun prepare(point: GeoPoint, address: String) {
        _point.value = point
        _address.value = address
        _reverseAddress.value = null
        _mapCenter.value = point
    }

    /**
     * 修改草稿点。
     *
     * @param address 非 null 表示已知地址；null 表示需要逆地理编码，
     *                调用方（ViewModel）负责异步解析后调用 [setReverseAddress]
     */
    fun setPoint(point: GeoPoint, address: String?) {
        _point.value = point
        _mapCenter.value = point
        if (address != null) {
            _address.value = address
            _reverseAddress.value = null
        } else {
            _address.value = ""
            _reverseAddress.value = null
        }
    }

    fun setReverseAddress(value: String?) {
        _reverseAddress.value = value
    }

    fun setMapCenter(point: GeoPoint) {
        _mapCenter.value = point
    }

    /** 展示用地址：直接地址优先，其次逆地理结果，最后回落为坐标描述。 */
    fun displayAddress(fallbackPoint: GeoPoint? = _point.value): String {
        val direct = _address.value
        if (direct.isNotBlank()) return direct
        val reverse = _reverseAddress.value
        if (!reverse.isNullOrBlank()) return reverse
        val target = fallbackPoint ?: return DefaultLocation.ADDRESS
        return StringsHolder.text { place.mockPickAddress(target.formatted()) }
    }
}
