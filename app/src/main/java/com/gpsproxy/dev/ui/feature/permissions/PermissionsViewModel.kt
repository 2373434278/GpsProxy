package com.gpsproxy.dev.ui.feature.permissions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.device.PermissionSnapshot
import com.gpsproxy.dev.core.device.PermissionUtils
import com.gpsproxy.dev.core.messaging.Messenger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 权限页 ViewModel。
 *
 * 权限状态没有可靠的系统回调（尤其是 AppOps 的"模拟位置应用"），
 * 因此采用"页面恢复时主动探测"的策略；探测逻辑集中在 [PermissionUtils]。
 */
class PermissionsViewModel(
    application: Application,
    private val messenger: Messenger,
) : AndroidViewModel(application) {

    private val _permissions = MutableStateFlow(PermissionUtils.snapshot(application))
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    fun refresh() {
        _permissions.value = PermissionUtils.snapshot(getApplication())
    }

    /** 定位权限弹窗返回后调用：刷新并给出结果提示。 */
    fun refreshWithLocationMessage() {
        refresh()
        messenger.show(
            {
                if (_permissions.value.locationGranted) onboarding.locationGranted
                else onboarding.locationDenied
            },
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                PermissionsViewModel(app, AppGraph.messenger)
            }
        }
    }
}
