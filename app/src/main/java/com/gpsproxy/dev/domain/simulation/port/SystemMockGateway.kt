package com.gpsproxy.dev.domain.simulation.port

import com.gpsproxy.dev.domain.simulation.model.ChannelId

/**
 * 系统测试提供者的挂载结果。
 *
 * @param installed 成功挂载的通道
 * @param providerNames 成功挂载的提供者名（`gps`/`network`/`fused`），用于展示与诊断
 * @param failures 每个失败提供者的原因
 */
data class InstallReport(
    val installed: List<ChannelId> = emptyList(),
    val providerNames: List<String> = emptyList(),
    val failures: List<String> = emptyList(),
)

/**
 * 一次系统 Mock 写入结果。
 *
 * @param hits 实际写入成功的通道，用于命中计数
 * @param providerNames 写入成功的提供者名
 * @param failures 失败原因（不同 ROM 提供者可用性差异很大）
 */
data class PushReport(
    val hits: List<ChannelId> = emptyList(),
    val providerNames: List<String> = emptyList(),
    val failures: List<String> = emptyList(),
) {
    val success: Boolean get() = hits.isNotEmpty()
}

/**
 * 官方 Mock 通道端口（`android.location.LocationManager` 测试提供者）。
 *
 * 该通道作用于宿主全部应用，坐标会被系统打上 `isMock=true` 标记——这是
 * 产品明确不隐藏的能力边界，因此接口只做读写，不提供任何伪造标记的能力。
 */
interface SystemMockGateway {

    /** 已挂载的提供者名，供启动时写入状态快照。 */
    val installedProviderNames: List<String>

    /** 挂载 GPS / Network / fused 测试提供者（幂等）。 */
    fun install(): InstallReport

    fun push(sample: LocationSample): PushReport

    /** 卸载全部测试提供者，恢复真实定位。 */
    fun teardown()
}
