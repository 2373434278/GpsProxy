package com.gpsproxy.dev.domain.diagnostics

import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.core.log.LogEntry
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.simulation.model.ChannelState
import com.gpsproxy.dev.domain.simulation.model.SimulationState

/**
 * 生成诊断包所需的**只读快照**。
 *
 * 传入快照而不是让构建器自己到处取数据，好处是：
 * - 报告内容可以在单测里用固定快照断言；
 * - 导出时刻的数据是一致的，不会出现"设备信息是导入前的、模拟状态是导出后的"；
 * - 敏感字段的取舍集中在一处（见 [DiagnosticsReportBuilder] 的脱敏说明）。
 */
data class DiagnosticsSnapshot(
    val generatedAtMillis: Long,
    val appVersion: String,
    val buildType: String,
    val manufacturer: String,
    val model: String,
    val apiLevel: Int,
    val supportedAbis: List<String>,
    val settings: AppSettings,
    val simulation: SimulationState,
    val channels: List<ChannelState>,
    val containerAvailable: Boolean,
    val containerAppCount: Int,
    val mockLocationAllowed: Boolean,
    val logs: List<LogEntry>,
) {
    /** 当前注入通道是否会带 isMock 标记（产品能力边界，必须如实写入报告）。 */
    val isMockFlag: Boolean
        get() = (simulation.injectionChannel ?: settings.injectionChannel) ==
            com.gpsproxy.dev.domain.settings.InjectionChannel.OFFICIAL
}

/**
 * 诊断报告构建器（纯函数）。
 *
 * 脱敏约定：
 * - **绝不**写入高德 Web 服务 Key、容器内应用包名清单、用户收藏/历史；
 * - 坐标属于本产品的核心输入，保留选点与注入点用于排查；
 * - 只保留容器应用**数量**，不保留具体包名，避免报告泄露用户装了哪些应用。
 */
class DiagnosticsReportBuilder {

    /**
     * @param strings 导出时刻的语言目录；通道名称与备注按写入时的语言固定，
     *   不随日后切换语言而改变（历史文件保持自洽）。
     */
    fun build(
        snapshot: DiagnosticsSnapshot,
        strings: Strings = StringsHolder.current,
    ): DiagnosticsReport = DiagnosticsReport(
        sections = linkedMapOf(
            FILE_DEVICE to deviceReport(snapshot),
            FILE_SETTINGS to settingsReport(snapshot),
            FILE_CHANNELS to channelsReport(snapshot, strings),
            FILE_SESSION to sessionReport(snapshot),
            FILE_LOGS to logsReport(snapshot),
        ),
    )

    private fun deviceReport(snapshot: DiagnosticsSnapshot): String = buildString {
        appendLine("GpsProxy diagnostics")
        appendLine("generated_at=${java.util.Date(snapshot.generatedAtMillis)}")
        appendLine("app_version=${snapshot.appVersion}")
        appendLine("build_type=${snapshot.buildType}")
        appendLine("manufacturer=${snapshot.manufacturer}")
        appendLine("model=${snapshot.model}")
        appendLine("android=${snapshot.apiLevel}")
        appendLine("abis=${snapshot.supportedAbis.joinToString(",")}")
        appendLine("mock_app_permission=${snapshot.mockLocationAllowed}")
        appendLine("container_available=${snapshot.containerAvailable}")
        appendLine("container_apps=${snapshot.containerAppCount}")
        appendLine("is_mock_flag=${snapshot.isMockFlag}")
    }

    private fun settingsReport(snapshot: DiagnosticsSnapshot): String {
        val settings = snapshot.settings
        return buildString {
            appendLine("coord_system=${settings.coordSystem.name}")
            appendLine("accuracy_m=${settings.accuracyMeters}")
            appendLine("drift_enabled=${settings.driftEnabled}")
            appendLine("blur_enabled=${settings.blurEnabled}")
            appendLine("blur_radius_m=${settings.blurRadiusMeters}")
            appendLine("update_interval_ms=${settings.updateIntervalMs}")
            appendLine("walk_speed_kmh=${settings.walkSpeedKmh}")
            appendLine("report_speed_kmh=${settings.reportSpeedKmh}")
            appendLine("report_bearing_deg=${settings.reportBearingDeg}")
            appendLine("altitude_m=${settings.altitudeMeters}")
            appendLine("auto_stop_minutes=${settings.autoStopMinutes}")
            appendLine("injection_channel=${settings.injectionChannel.name}")
            appendLine("notification_detail=${settings.notificationDetail.name}")
            appendLine("boot_restore=${settings.bootRestore}")
            appendLine("overlay_enabled=${settings.overlayEnabled}")
            appendLine("detail_level=${settings.detailLevel.name}")
            appendLine("last_self_check_at=${settings.lastSelfCheckAt}")
        }
    }

    private fun channelsReport(snapshot: DiagnosticsSnapshot, strings: Strings): String = buildString {
        snapshot.channels.forEach { channel ->
            val name = strings.channel.channelName(channel.id.name)
            val note = channel.note
                ?.let { strings.channel.note(it.kind.name, it.count) }
                ?: strings.channel.channelDescription(channel.id.name)
            appendLine(
                "${channel.id.name}\t${channel.status.name}\tcount=${channel.count}\t" +
                    "$name\t$note",
            )
        }
    }

    private fun sessionReport(snapshot: DiagnosticsSnapshot): String {
        val state = snapshot.simulation
        return buildString {
            appendLine("mode=${state.mode.name}")
            appendLine("run_state=${state.runState.name}")
            appendLine("route_kind=${state.route?.kind?.name ?: "-"}")
            appendLine("route_source=${state.routeSource?.name ?: "-"}")
            appendLine("waypoints=${state.route?.waypoints?.size ?: 0}")
            appendLine("speed_factor=${state.speedFactor}")
            appendLine("auto_stop_at=${state.autoStopAtMillis}")
            appendLine("walking=${state.walking}")
            appendLine("selected=${state.point?.formatted() ?: "-"}")
            appendLine("injected=${state.injectedPoint?.formatted() ?: "-"}")
            appendLine("blur_radius_m=${state.blurRadiusMeters}")
            appendLine("accuracy_m=${state.accuracy}")
            appendLine("altitude_m=${state.altitude}")
            appendLine("satellites=${state.satellites}")
            appendLine("speed_kmh=${state.speedKmh}")
            appendLine("bearing=${state.bearing}")
            appendLine("injected_count=${state.injectedCount}")
            appendLine("error_code=${state.errorCode?.code ?: "-"}")
            state.route?.let { route ->
                appendLine("route_mode=${route.mode.name}")
                appendLine("route_speed_kmh=${route.speedKmh}")
                appendLine("route_loop=${route.loop}")
                appendLine("route_round_trip=${route.roundTrip}")
                appendLine("route_source=${state.routeSource?.name ?: "-"}")
            }
            state.progress?.let { progress ->
                appendLine("route_total_m=${progress.distanceTotalM}")
                appendLine("route_done_m=${progress.distanceDoneM}")
                appendLine("route_fraction=${progress.fraction}")
            }
        }
    }

    private fun logsReport(snapshot: DiagnosticsSnapshot): String = buildString {
        snapshot.logs.forEach {
            appendLine("${it.timeText()} [${it.severity.name}] ${it.message}")
        }
    }

    companion object {
        const val FILE_DEVICE = "device.txt"
        const val FILE_SETTINGS = "settings.txt"
        const val FILE_CHANNELS = "channels.txt"
        const val FILE_SESSION = "session.txt"
        const val FILE_LOGS = "logs.txt"
    }
}

/** 诊断包内容：文件名 → 文本内容。 */
data class DiagnosticsReport(val sections: Map<String, String>)
