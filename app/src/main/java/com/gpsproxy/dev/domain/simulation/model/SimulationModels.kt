package com.gpsproxy.dev.domain.simulation.model

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.GeoPoint

enum class SimMode { IDLE, SINGLE, ROUTE }

enum class RunState { IDLE, RUNNING, PAUSED, COMPLETED, ERROR }

enum class TravelMode(val defaultSpeedKmh: Int) {
    DRIVING(32),
    CYCLING(15),
    WALKING(5),
}

/**
 * 轨迹种类。
 *
 * 四种轨迹共用同一条状态机、帧泵与进度器，只在"如何得到折线"上分流：
 * - [POINT_TO_POINT]：起终点 + 可选途经点，走高德/内置曲线；
 * - [ROAM]：以起点为圆心生成闭合随机环线，强制循环；
 * - [IMPORTED]：直接使用 [RouteConfig.trackPoints]（GPX 导入）。
 */
enum class RouteKind {
    POINT_TO_POINT,
    ROAM,
    IMPORTED,
}

data class RouteConfig(
    val start: GeoPoint,
    val end: GeoPoint,
    val mode: TravelMode = TravelMode.DRIVING,
    val speedKmh: Int = 32,
    val loop: Boolean = false,
    val roundTrip: Boolean = false,
    /** 起终点之间的途经点，按顺序连接。 */
    val waypoints: List<GeoPoint> = emptyList(),
    val kind: RouteKind = RouteKind.POINT_TO_POINT,
    /** [RouteKind.IMPORTED] 使用的完整折线。 */
    val trackPoints: List<GeoPoint> = emptyList(),
    /** [RouteKind.ROAM] 的漫游半径（米）。 */
    val roamRadiusMeters: Int = 500,
) {
    val speedMps: Double get() = speedKmh / 3.6

    /** 起终点之间的所有停靠点（不含首尾），供规划器与预览复用。 */
    val stops: List<GeoPoint>
        get() = buildList {
            add(start)
            addAll(waypoints)
            add(end)
        }
}

/**
 * 摇杆行走的方向与速度。
 *
 * [bearingDeg] 采用 0=正北、90=正东的方位角；[speedMps] 为 0 时视为停止。
 */
data class MotionVector(
    val bearingDeg: Float,
    val speedMps: Float,
) {
    val normalizedBearing: Float get() = ((bearingDeg % 360f) + 360f) % 360f
}

data class RouteProgress(
    val fraction: Float = 0f,
    val distanceDoneM: Double = 0.0,
    val distanceTotalM: Double = 0.0,
    val remainingSeconds: Int = 0,
    val pointIndex: Int = 0,
    val totalPoints: Int = 0,
)

enum class ChannelStatus { OK, WARN, BAD, OFF }

/**
 * 注入通道的身份。
 *
 * 用枚举替代散落的字符串 id（`"gps"`、`"container"`…）：旧实现里通道状态更新
 * 依赖字符串比较，拼错不会编译失败，只会静默丢失一条状态。
 */
enum class ChannelId {
    CONTAINER,
    GPS,
    NETWORK,
    FUSED,
    SDK,
    SENSOR,
    CELL,
    WIFI,
}

/**
 * 通道状态备注的语义单元。
 *
 * 备注里可能带数量等运行期参数，因此不能直接存成字符串：
 * 文案在渲染时才按当前语言生成，历史和日志不会出现"切换语言后旧文案变味"。
 */
enum class ChannelNoteKind {
    CONTAINER_READY,
    CONTAINER_EMPTY,
    CONTAINER_UNAVAILABLE,
    CONTAINER_IDLE,
    MOUNTED_OFFICIAL,
    NOT_MOUNTED,
    MOUNTED_FUSED,
    FUSED_NOT_EXPOSED,
    SYNCED,
    RUNTIME_READY,
    MOCK_STANDBY,
    NOT_MOCK_APP,
    FOLLOWS_CONTAINER,
    STOPPED_CONTAINER,
    STOPPED,
    FUSED_FORWARD_ONLY,
    NOT_FORCED,
    CONTAINER_ONLY,
    NOT_PROVIDED,
    CONTAINER_ACTIVE,
    HOST_OUT_OF_SCOPE,
}

data class ChannelNote(val kind: ChannelNoteKind, val count: Int = 0)

data class ChannelState(
    val id: ChannelId,
    val status: ChannelStatus,
    val count: Long = 0,
    val note: ChannelNote? = null,
)

/**
 * 模拟会话的对外快照（UI 只读）。
 *
 * 这是**投影**：由领域状态机 + 通道状态 + 设置实时合成，而不是可被随意改写的
 * 全局可变对象。UI 只读取它，所有变更都必须经过 `SimulationEngine` 的用例方法，
 * 从而杜绝"状态字段被某处顺手改掉"的隐性耦合。
 */
data class SimulationState(
    val mode: SimMode = SimMode.IDLE,
    val runState: RunState = RunState.IDLE,
    /** 用户选定的真实目标点；收藏、历史、地址都以它为准。 */
    val point: GeoPoint? = null,
    /** 实际写入定位通道的坐标。开启位置模糊后它是 [point] 平移后的结果。 */
    val injectedPoint: GeoPoint? = null,
    /** 生效中的模糊半径，0 表示未开启模糊。 */
    val blurRadiusMeters: Int = 0,
    val address: String = "",
    val speedKmh: Double = 0.0,
    val bearing: Float = 0f,
    val accuracy: Int = 5,
    val altitude: Double = 12.0,
    /** 可见卫星数，写入 Location.extras 的 satellites 字段，导航类应用会读取。 */
    val satellites: Int = 12,
    val injectedCount: Long = 0,
    val startedAt: Long = 0,
    val route: RouteConfig? = null,
    /** 本次轨迹的来源（真实路网 / 内置曲线），UI 必须如实标注。 */
    val routeSource: com.gpsproxy.dev.domain.simulation.route.RouteSourceKind? = null,
    /** 本次巡航的完整轨迹（未模糊），供地图绘制；避免 UI 自行重复规划。 */
    val routePathPoints: List<GeoPoint> = emptyList(),
    /** 路网规划失败并回退到内置曲线时的原因；正常规划时为 null。 */
    val routeDegradeReason: String? = null,
    val progress: RouteProgress? = null,
    val installedProviders: List<String> = emptyList(),
    val errorCode: ErrorCode? = null,
    /** 轨迹倍速（0.25–8），UI 暴露 0.5x–4x。 */
    val speedFactor: Float = 1f,
    /** 定时停止的墙钟截止时间（毫秒）；0 表示未设置。 */
    val autoStopAtMillis: Long = 0L,
    /** 单点模式下摇杆是否正在推动位置。 */
    val walking: Boolean = false,
    /**
     * 本次会话实际使用的通道（启动时固定）。
     *
     * 与设置里的"当前选择"分开：会话中途改设置不会切换通道，
     * 诊断报告与通知必须用这份真实值，否则会出现"报告说容器、实际写官方"的误导。
     */
    val injectionChannel: com.gpsproxy.dev.domain.settings.InjectionChannel? = null,
) {
    val isActive: Boolean
        get() = mode != SimMode.IDLE && (runState == RunState.RUNNING || runState == RunState.PAUSED)
}
