package com.gpsproxy.dev.platform.device

import android.content.Context
import com.gpsproxy.dev.core.device.PermissionUtils
import com.gpsproxy.dev.domain.simulation.port.PermissionFacts

/**
 * 把 Android 的 AppOps / 权限查询适配成领域端口。
 *
 * 这一层刻意保持极薄：任何判断逻辑都应留在领域用例里，
 * 平台层只负责"读事实"，方便在测试中替换为任意组合。
 */
class AndroidPermissionFacts(private val context: Context) : PermissionFacts {

    override fun isMockLocationAllowed(): Boolean =
        PermissionUtils.isMockLocationAllowed(context)

    override fun locationGranted(): Boolean =
        PermissionUtils.snapshot(context).locationGranted
}
