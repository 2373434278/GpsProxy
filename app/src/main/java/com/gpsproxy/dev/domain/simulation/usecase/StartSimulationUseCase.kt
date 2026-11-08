package com.gpsproxy.dev.domain.simulation.usecase

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.result.DomainError
import com.gpsproxy.dev.core.result.Outcome
import com.gpsproxy.dev.domain.container.ContainerRepository
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.port.ContainerGateway
import com.gpsproxy.dev.domain.simulation.port.PermissionFacts
import com.gpsproxy.dev.domain.simulation.port.SimulationStarter
import com.gpsproxy.dev.domain.simulation.route.RouteSourceKind

/** 被阻断时，UI 应该把用户引导到哪个页面。 */
enum class StartBlockAction {
    /** 容器内确实没有应用，需要去导入。 */
    OPEN_CONTAINER_APPS,

    /** 容器运行时/服务本身不可用，需要去容器空间看自检。 */
    OPEN_CONTAINER_INIT,

    /** 容器服务短暂不可达（进程启动或被冻结），重试即可，不要误导用户去导入。 */
    RETRY,
    OPEN_DEVELOPER_OPTIONS,
    OPEN_PERMISSIONS,
    NONE,
}

sealed interface StartResult {
    data class Started(val routeSource: RouteSourceKind? = null) : StartResult
    data class Blocked(val error: DomainError, val action: StartBlockAction) : StartResult
}

/**
 * 启动模拟的用例。
 *
 * 旧实现把同一条前置条件链在 `startSimulation()` 与 `startRoute()` 里各写了一遍
 * （约 40 行 × 2），任何规则调整（例如容器必须已有应用）都要改两处，
 * 而且两处的提示文案已经开始出现细微差异。
 *
 * 现在规则与文案只在这里；UI 只需根据 [StartBlockAction] 决定跳转到哪个页面。
 * 引擎内部仍会做一次通道预检，那是为了防御直接调用引擎的路径（服务/测试），
 * 两层职责不冲突：这里负责"用户可见的引导"，引擎负责"通道是否真的可用"。
 */
class StartSimulationUseCase(
    private val engine: SimulationStarter,
    private val permissions: PermissionFacts,
    private val container: ContainerGateway,
    private val containerApps: ContainerRepository,
) {

    suspend fun startSingle(): StartResult {
        readiness()?.let { return it }
        return when (val outcome = engine.startSingle()) {
            is Outcome.Success -> StartResult.Started()
            is Outcome.Failure -> StartResult.Blocked(outcome.error, actionFor(outcome.error.code))
        }
    }

    suspend fun startRoute(config: RouteConfig): StartResult {
        readiness()?.let { return it }
        return when (val outcome = engine.startRoute(config)) {
            is Outcome.Success -> StartResult.Started(engine.state.value.routeSource)
            is Outcome.Failure -> StartResult.Blocked(outcome.error, actionFor(outcome.error.code))
        }
    }

    /** @return 非 null 表示当前环境不满足启动条件 */
    private suspend fun readiness(): StartResult.Blocked? = when (engine.currentSettings().injectionChannel) {
        InjectionChannel.CONTAINER -> when {
            !container.available -> blocked(
                ErrorCode.BOX_0101,
                "容器运行时不可用",
                StartBlockAction.OPEN_CONTAINER_INIT,
            )
            else -> {
                // 只查一次清单：Binder 调用较慢，重复查询会让"开始模拟"多等一轮往返。
                val apps = containerApps.listApps()
                when {
                    // null 表示服务暂时不可达（:black 启动中或被系统冻结），
                    // 与"确实没有导入应用"是两回事，不能都引导去导入。
                    apps == null -> blocked(
                        ErrorCode.BOX_0101,
                        "容器服务暂时不可达",
                        StartBlockAction.RETRY,
                    )
                    apps.isEmpty() -> blocked(
                        ErrorCode.BOX_0103,
                        "容器内没有可接管的应用",
                        StartBlockAction.OPEN_CONTAINER_APPS,
                    )
                    else -> null
                }
            }
        }

        InjectionChannel.OFFICIAL -> when {
            !permissions.isMockLocationAllowed() -> blocked(
                ErrorCode.PERM_0102,
                "未在开发者选项中设为模拟位置应用",
                StartBlockAction.OPEN_DEVELOPER_OPTIONS,
            )
            !permissions.locationGranted() -> blocked(
                ErrorCode.PERM_0104,
                "前台定位服务需要定位权限",
                StartBlockAction.OPEN_PERMISSIONS,
            )
            else -> null
        }
    }

    private fun blocked(code: ErrorCode, cause: String, action: StartBlockAction) =
        StartResult.Blocked(DomainError(code = code, cause = cause), action)

    private fun actionFor(code: ErrorCode): StartBlockAction = when (code) {
        ErrorCode.BOX_0101 -> StartBlockAction.OPEN_CONTAINER_INIT
        ErrorCode.BOX_0103, ErrorCode.BOX_0102 -> StartBlockAction.OPEN_CONTAINER_APPS
        ErrorCode.PERM_0102 -> StartBlockAction.OPEN_DEVELOPER_OPTIONS
        ErrorCode.PERM_0104 -> StartBlockAction.OPEN_PERMISSIONS
        else -> StartBlockAction.NONE
    }
}
