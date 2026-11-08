package com.gpsproxy.dev.domain.simulation.port

/**
 * 权限事实端口。
 *
 * 领域层需要判断「是否被设为模拟位置应用」来决定错误码与启动前置条件，
 * 但不应该直接读 AppOpsManager。把事实抽成端口后，启动用例可以在
 * 任意组合（已授权/未授权 × 有定位/无定位）下被测试覆盖。
 */
interface PermissionFacts {

    /** 是否已在开发者选项中被设为「模拟位置信息应用」。 */
    fun isMockLocationAllowed(): Boolean

    /** 是否已授予定位权限（精确或粗略任一）。 */
    fun locationGranted(): Boolean
}
