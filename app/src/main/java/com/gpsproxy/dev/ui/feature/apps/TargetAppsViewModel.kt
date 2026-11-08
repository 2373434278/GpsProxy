package com.gpsproxy.dev.ui.feature.apps

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.core.messaging.MessageTone
import com.gpsproxy.dev.core.messaging.Messenger
import com.gpsproxy.dev.data.InstalledApp
import com.gpsproxy.dev.data.SettingsRepository
import com.gpsproxy.dev.data.TargetAppsRepository
import com.gpsproxy.dev.domain.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 目标应用页 ViewModel。
 *
 * 应用清单查询较慢（PackageManager 遍历），因此做了"只加载一次 + 支持强制刷新"，
 * 与旧实现一致；选中集合仍然持久化在设置里。
 */
class TargetAppsViewModel(
    private val targetRepo: TargetAppsRepository,
    private val settingsRepo: SettingsRepository,
    private val messenger: Messenger,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var loaded = false

    fun ensureAppsLoaded(force: Boolean = false) {
        if (_loading.value) return
        if (loaded && !force) return
        viewModelScope.launch {
            _loading.value = true
            _installedApps.value = targetRepo.loadLaunchableApps()
            _loading.value = false
            loaded = true
        }
    }

    suspend fun loadIcon(packageName: String): Bitmap? = targetRepo.loadIcon(packageName)

    /** 兼容界面既有命名。 */
    suspend fun loadAppIcon(packageName: String): Bitmap? = loadIcon(packageName)

    fun toggleTargetPackage(packageName: String) {
        viewModelScope.launch {
            val current = settings.value.targetPackages
            val next = if (current.contains(packageName)) {
                current - packageName
            } else {
                current + packageName
            }
            settingsRepo.setTargetPackages(next)
            messenger.show(
                {
                    if (current.contains(packageName)) container.removedCommon else container.addedCommon
                },
                MessageTone.SUCCESS,
            )
        }
    }

    fun launchApp(packageName: String) {
        if (!targetRepo.launch(packageName)) {
            messenger.show({ container.cannotLaunchApp })
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TargetAppsViewModel(
                    targetRepo = AppGraph.targetApps,
                    settingsRepo = AppGraph.settings,
                    messenger = AppGraph.messenger,
                )
            }
        }
    }
}
