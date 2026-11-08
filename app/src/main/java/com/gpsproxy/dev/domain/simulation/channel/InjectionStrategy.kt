package com.gpsproxy.dev.domain.simulation.channel

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.domain.simulation.port.LocationSample

/** 通道就绪检查结果。 */
data class ChannelReadiness(
    val ready: Boolean,
    val error: ErrorCode? = null,
    val reason: String = "",
) {
    companion object {
        fun ready(): ChannelReadiness = ChannelReadiness(true)
        fun blocked(error: ErrorCode, reason: String): ChannelReadiness =
            ChannelReadiness(ready = false, error = error, reason = reason)
    }
}

/**
 * 一次写入的报告，由引擎统一转成通道状态、命中计数与错误处理。
 *
 * 通道实现不直接改 UI 状态，也不自己推导计数，避免"某个通道忘了记一次命中"。
 */
data class PublishReport(
    val success: Boolean,
    val hits: List<com.gpsproxy.dev.domain.simulation.model.ChannelId> = emptyList(),
    val providerNames: List<String> = emptyList(),
    val injectedDelta: Int = 0,
    val failures: List<String> = emptyList(),
)

/**
 * 注入策略。
 *
 * 两条通道的差异集中在三个动作：准备、单帧写入、收尾。此前这些差异用
 * `when (settings.injectionChannel)` 分布在 `ensureChannel`、`pushAt`、
 * `channelErrorCode`、`channelErrorReason` 四个方法里，新增通道必须同步改四处。
 * 抽成策略后，新增一条通道只需新增一个实现并注册到工厂。
 *
 * 关键产品约束：**容器通道失败时绝不静默回落到官方通道**。
 * 否则用户以为自己在用免 root 通道，坐标却被打上 isMock 标记。
 */
interface InjectionStrategy {

    /** 通道展示名，用于日志与失败提示。 */
    val label: String

    /** 该通道失败时对外的错误码（会随权限状态变化）。 */
    val failureError: ErrorCode

    /** 失败原因文案，供启动失败提示使用。 */
    val failureReason: String

    fun prepare(): ChannelReadiness

    fun publish(sample: LocationSample): PublishReport

    /** 释放该通道持有的系统资源，可重复调用。 */
    fun release()
}
