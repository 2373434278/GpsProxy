package com.gpsproxy.dev.domain.simulation.channel

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.log.LogSink
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.simulation.model.ChannelId
import com.gpsproxy.dev.domain.simulation.port.ContainerGateway
import com.gpsproxy.dev.domain.simulation.port.LocationSample
import com.gpsproxy.dev.domain.simulation.port.PermissionFacts
import com.gpsproxy.dev.domain.simulation.port.SystemMockGateway

/**
 * 容器代理通道（默认、免 root）。
 *
 * 只把坐标交给容器内定位代理，不写系统测试提供者，因此不会产生 isMock 标记。
 * 容器不可用时**直接失败**，不做任何降级写入——否则用户会误以为自己正在使用
 * 免 root 通道，而实际坐标已被系统标记。
 */
class ContainerInjectionStrategy(
    private val container: ContainerGateway,
    private val monitor: ChannelMonitor,
    private val cells: CellFingerprintPublisher,
    private val log: LogSink,
) : InjectionStrategy {

    override val label: String = "容器代理"
    override val failureError: ErrorCode = ErrorCode.BOX_0101
    override val failureReason: String = "容器代理通道未就绪"

    override fun prepare(): ChannelReadiness {
        if (!container.available) {
            monitor.markContainerUnavailable()
            log.error { log.containerUnavailable }
            return ChannelReadiness.blocked(ErrorCode.BOX_0101, failureReason)
        }
        val enabled = container.enableAll()
        if (enabled <= 0) {
            monitor.markContainerEmpty()
            log.warn { log.containerNoApps }
            // 与旧版一致：对外仍统一报 BOX-0101/「通道未就绪」，
            // "没有可接管应用"属于用户操作提示，由上层准备阶段单独给出。
            return ChannelReadiness.blocked(ErrorCode.BOX_0101, failureReason)
        }
        monitor.markContainerReady(enabled)
        return ChannelReadiness.ready()
    }

    override fun publish(sample: LocationSample): PublishReport {
        if (!container.push(sample)) {
            return PublishReport(success = false, failures = listOf("container push failed"))
        }
        val hits = mutableListOf(ChannelId.CONTAINER)
        // 基站指纹与坐标同城推导，避免坐标在北京而基站落在别处被风控识别
        if (cells.publish(sample.point, sample.satellites)) {
            hits += ChannelId.CELL
        }
        return PublishReport(
            success = true,
            hits = hits,
            injectedDelta = 1,
        )
    }

    override fun release() {
        container.disableAll()
        container.clearCell()
        cells.reset()
    }
}

/**
 * 系统 Mock 通道。
 *
 * 写入 Android 官方测试提供者，作用于宿主全部应用，坐标会被系统打上
 * `isMock=true` 标记——本实现刻意不做隐藏，这是产品明确的能力边界。
 *
 * 容器可用时顺带同步一份容器坐标，让沙箱应用与宿主应用看到同一份位置；
 * 同步失败不影响官方通道本身的成功判定。
 */
class OfficialMockInjectionStrategy(
    private val mock: SystemMockGateway,
    private val container: ContainerGateway,
    private val monitor: ChannelMonitor,
    private val permissions: PermissionFacts,
    private val cells: CellFingerprintPublisher,
    private val log: LogSink,
    private val elapsedMillis: () -> Long,
) : InjectionStrategy {

    /** 顺带下发失败后的冷却截止时间，避免 :black 被冻结时每帧都打一次必然失败的 Binder。 */
    private var containerPushSuspendedUntil = 0L

    override val label: String = "系统 Mock"

    override val failureError: ErrorCode
        get() = if (permissions.isMockLocationAllowed()) ErrorCode.INJ_0101 else ErrorCode.PERM_0102

    override val failureReason: String
        get() = if (permissions.isMockLocationAllowed()) {
            "测试提供者挂载失败"
        } else {
            "未在开发者选项中设为模拟位置应用"
        }

    override fun prepare(): ChannelReadiness {
        val report = mock.install()
        monitor.onInstalled(report)
        if (report.failures.isNotEmpty()) {
            log.warn { log.providerIssues(report.failures.joinToString("; ")) }
        }
        return if (report.installed.isNotEmpty()) {
            ChannelReadiness.ready()
        } else {
            ChannelReadiness.blocked(failureError, failureReason)
        }
    }

    override fun publish(sample: LocationSample): PublishReport {
        val result = mock.push(sample)
        if (!result.success) {
            log.error { log.officialPushFailed(result.failures.joinToString("; ")) }
            return PublishReport(success = false, failures = result.failures)
        }
        val hits = result.hits.toMutableList()
        var injectedDelta = result.providerNames.size
        // 容器通道同时可用时顺带下发，保证两类应用看到同一份坐标。
        // 容器进程被冻结时会持续失败，失败后短暂退避，避免每帧刷一次异常与重连。
        if (container.available && pushToContainer(sample)) {
            hits += ChannelId.CONTAINER
            injectedDelta += 1
            if (cells.publish(sample.point, sample.satellites)) {
                hits += ChannelId.CELL
            }
        }
        return PublishReport(
            success = true,
            hits = hits,
            providerNames = result.providerNames,
            injectedDelta = injectedDelta,
        )
    }

    private fun pushToContainer(sample: LocationSample): Boolean {
        val now = elapsedMillis()
        if (now < containerPushSuspendedUntil) return false
        if (container.push(sample)) return true
        containerPushSuspendedUntil = now + CONTAINER_PUSH_RETRY_BACKOFF_MS
        log.warn { log.containerMirrorRetry(CONTAINER_PUSH_RETRY_BACKOFF_MS) }
        return false
    }

    override fun release() {
        mock.teardown()
    }

    private companion object {
        /** 顺带下发失败后的退避窗口；到期自动重试，不需要重启会话。 */
        const val CONTAINER_PUSH_RETRY_BACKOFF_MS = 10_000L
    }
}

/**
 * 按用户设置选择策略。
 *
 * 用工厂而不是在引擎里 `when`：设置项只在**开始一次会话时**读取一次，
 * 会话中途改设置不会切换通道（否则会出现"前半程免 root、后半程 isMock"的
 * 诡异状态）。下一次开始时自然生效。
 */
class InjectionStrategyFactory(
    private val container: ContainerInjectionStrategy,
    private val official: OfficialMockInjectionStrategy,
) {

    fun forChannel(channel: InjectionChannel): InjectionStrategy = when (channel) {
        InjectionChannel.CONTAINER -> container
        InjectionChannel.OFFICIAL -> official
    }
}
