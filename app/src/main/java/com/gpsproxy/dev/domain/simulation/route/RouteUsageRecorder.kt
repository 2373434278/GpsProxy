package com.gpsproxy.dev.domain.simulation.route

import com.gpsproxy.dev.domain.simulation.model.RouteConfig

/**
 * 轨迹使用记录端口。
 *
 * 引擎在"轨迹真正启动成功"后通知实现方记录最近路线；与 [com.gpsproxy.dev.domain.session.SessionRecorder]
 * 分开：会话恢复只关心"是否还在运行"，路线库关心"用过哪条路线"，
 * 两者的持久化生命周期与清理规则不同。
 */
interface RouteUsageRecorder {

    suspend fun onRouteStarted(config: RouteConfig)

    companion object {
        /** 默认空实现：测试或无路线库的场景不产生记录。 */
        val Noop: RouteUsageRecorder = object : RouteUsageRecorder {
            override suspend fun onRouteStarted(config: RouteConfig) = Unit
        }
    }
}
