package com.gpsproxy.dev.platform.service

import android.annotation.SuppressLint
import android.content.pm.ServiceInfo
import com.gpsproxy.dev.domain.simulation.port.PermissionFacts

/**
 * 前台服务类型选择策略。
 *
 * 前台服务类型决定系统按哪套权限校验：
 * - 系统 Mock 通道需要真实定位参与，用 `location` 类型；
 * - 容器代理通道不读真实位置。若仍声明 `location`，Android 14+ 会因为
 *   未授予定位权限直接拒绝启动，把免 root 路径堵死。因此按授权情况降级为
 *   `specialUse`，让注入服务在无定位权限时也能常驻。
 */
@SuppressLint("InlinedApi") // 常量已内联，调用点由 resolve() 的版本判断保证安全
class ForegroundTypePolicy(private val permissions: PermissionFacts) {

    /** @return null 表示 API 34 以下无需显式类型 */
    fun resolve(apiLevel: Int): Int? {
        if (apiLevel < API_UPSIDE_DOWN_CAKE) return null
        return if (permissions.locationGranted()) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
    }

    private companion object {
        const val API_UPSIDE_DOWN_CAKE = 34
    }
}
