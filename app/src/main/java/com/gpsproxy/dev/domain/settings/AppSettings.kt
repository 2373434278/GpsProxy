package com.gpsproxy.dev.domain.settings

import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow

/** 日志详细程度；展示文案由设置页按当前语言解析。 */
enum class DetailLevel {
    NORMAL,
    VERBOSE,
}

/**
 * 前台常驻通知的详略程度。
 *
 * Android 不允许真正隐藏前台服务通知，因此这里控制的是**信息密度**而不是开关：
 * 精简模式只保留坐标与精度，详细模式追加通道、坐标系与模糊半径。
 */
enum class NotificationDetail {
    COMPACT,
    DETAILED,
}

/**
 * 注入通道。
 *
 * [CONTAINER] 是默认且推荐的主通道：坐标经 Binder 下发到虚拟容器进程，
 * 容器内系统服务代理在目标应用读取定位时替换返回值。全程不写系统测试提供者、
 * 不需要 root、不需要「开发者选项 → 模拟位置应用」授权，
 * 且容器内拿到的 Location 不带 isMock 标记。
 *
 * [OFFICIAL] 是系统级 Mock 通道，作用于宿主全部应用，但必须由用户在开发者
 * 选项中授权，坐标会被系统打上 isMock 标记。
 */
enum class InjectionChannel {
    CONTAINER,
    OFFICIAL,
}

/**
 * 用户偏好快照。
 *
 * 这是**不可变值对象**：任何修改都通过 `SettingsStore.update {}` 生成新实例，
 * 因此模拟循环可以安全地持有某一帧的快照，不必担心热路径上读到半更新状态。
 *
 * 字段顺序按「定位 → 外观 → 启动 → 调试」分组，新增字段必须同时更新
 * `SettingsCodec` 的读写表，单测会校验 encode/decode 定点性质。
 */
data class AppSettings(
    val coordSystem: CoordSystem = CoordSystem.GCJ02,
    val accuracyMeters: Int = 5,
    val driftEnabled: Boolean = true,
    val blurEnabled: Boolean = false,
    val blurRadiusMeters: Int = 200,
    val updateIntervalMs: Int = 1000,
    /** 摇杆行走的最大速度（km/h）。 */
    val walkSpeedKmh: Int = 5,
    /** 静止时上报给 Location 的速度（km/h）；只影响载荷，不产生位移。 */
    val reportSpeedKmh: Int = 0,
    /** 静止时上报的航向（度）；0 表示沿用上次航向。 */
    val reportBearingDeg: Int = 0,
    /** 写入 Location.altitude 的基准海拔（米），仍会叠加 ±1m 抖动。 */
    val altitudeMeters: Int = 12,
    /** 模拟时长上限（分钟）；0 表示不限。按墙钟计时，暂停不暂停计时。 */
    val autoStopMinutes: Int = 0,
    val overlayEnabled: Boolean = true,
    val notificationDetail: NotificationDetail = NotificationDetail.DETAILED,
    /** 界面语言；[AppLanguage.SYSTEM] 表示跟随系统。 */
    val language: AppLanguage = AppLanguage.SYSTEM,
    val bootRestore: Boolean = false,
    val detailLevel: DetailLevel = DetailLevel.NORMAL,
    val injectionChannel: InjectionChannel = InjectionChannel.CONTAINER,
    val disclaimerAccepted: Boolean = false,
    /** 上次自检完成时间（墙钟毫秒）；0 表示从未完成过。 */
    val lastSelfCheckAt: Long = 0L,
    val targetPackages: Set<String> = emptySet(),
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastAddress: String = "",
)

/**
 * 设置的读取与写入端口。
 *
 * 领域层只关心「当前设置是什么」与「记住上次选点」，不关心底层是
 * DataStore、SharedPreferences 还是测试内存实现。
 */
interface SettingsGateway {

    val settings: Flow<AppSettings>

    suspend fun rememberPoint(lat: Double, lng: Double, address: String)
}
