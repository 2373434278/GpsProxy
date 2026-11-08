package com.gpsproxy.dev.ui.feature.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.domain.place.DefaultLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 应用级启动状态。
 *
 * 只负责两件宿主进程启动时必须完成的事：
 * 1. 从持久化设置恢复上次选点，并交给模拟引擎；
 * 2. 告诉导航器应该从"引导页"还是"首页"开始。
 *
 * 原来的 `GpsProxyViewModel` 把这件事和 7 个页面的业务混在一起，
 * 导致启动页也要构造搜索、容器、诊断等全部依赖。
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _startHome = MutableStateFlow(false)
    val startHome: StateFlow<Boolean> = _startHome.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = AppGraph.settings.settings.first()
            // 在任何页面组合之前应用持久化语言，避免首帧中英闪烁。
            AppGraph.i18n.apply(stored.language)
            val point = if (stored.lastLat != null && stored.lastLng != null) {
                GeoPoint(stored.lastLat, stored.lastLng)
            } else {
                DefaultLocation.POINT
            }
            val address = stored.lastAddress.ifBlank { DefaultLocation.ADDRESS }
            AppGraph.engine.initialize(point, address)
            // 引擎可能在 Activity 重建后仍持有更"新"的选点，以它为准。
            val current = AppGraph.engine.state.value
            AppGraph.selection.prepare(
                point = current.point ?: point,
                address = current.address.ifBlank { address },
            )
            AppGraph.engine.refreshChannels()
            _startHome.value = stored.disclaimerAccepted
            _ready.value = true
        }
    }

    /** 回到前台时刷新通道环境（容器进程/授权可能已经变化）。 */
    fun refreshEnvironment() {
        AppGraph.engine.refreshChannels()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                AppViewModel(app)
            }
        }
    }
}
