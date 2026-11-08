package com.gpsproxy.dev.container

import com.gpsproxy.dev.GpsProxyApp
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.geo.CoordTransform
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.log.LogBus
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.system.ServiceManager
import top.niunaijun.blackbox.entity.location.BCell
import top.niunaijun.blackbox.entity.location.BLocation
import top.niunaijun.blackbox.fake.frameworks.BLocationManager
import top.niunaijun.blackbox.fake.frameworks.BPackageManager

/**
 * 宿主侧对 BlackBox 虚拟容器引擎的统一门面。
 *
 * 该对象只在宿主主进程使用，负责三件事：
 * 1. 探测容器运行时是否可用（引擎随 Application 启动时加载）；
 * 2. 管理容器内已导入的应用清单与启停；
 * 3. 把宿主算出的模拟坐标 / 基站数据推送到容器定位代理服务。
 *
 * 所有与容器 Binder 交互的方法都做了异常隔离：容器服务未就绪、:black 进程
 * 被杀、Binder 失效时返回安全默认值并清理服务缓存，由下一 tick 自动重连，
 * 避免宿主主进程因容器异常而崩溃。
 */
object ContainerEngine {

    /** 容器默认用户空间；BlackBox 支持多开，宿主只用 0 号用户。 */
    const val USER_ID = 0

    /** 容器定位代理服务可用的判断：引擎已挂载且 :black 服务可连通。 */
    val available: Boolean
        get() = GpsProxyApp.containerReady &&
            runCatching { BlackBoxCore.get().isServicesAvailable }.getOrDefault(false)

    /**
     * 读取容器内已导入的包名清单。
     *
     * 首次查询可能命中启动竞态（主进程缓存到失效 Binder），
     * 失败时重置 PMS 缓存后重试一次。
     */
    fun installedPackages(): List<String> {
        queryPackages()?.let { return it }
        resetPackageService()
        return queryPackages().orEmpty()
    }

    private fun queryPackages(): List<String>? = runCatching {
        BPackageManager.get().getInstalledPackages(0, USER_ID).map { it.packageName }
    }.getOrNull()

    // 启动竞态下主进程可能缓存到失效的 :black Binder，需要清缓存后重取
    fun resetPackageService() {
        runCatching { BlackBoxCore.get().clearServiceCache(ServiceManager.PACKAGE_MANAGER) }
        runCatching { BPackageManager.get().clearServiceCache() }
    }

    /**
     * 为单个容器应用开启模拟。
     *
     * @param ownMode true 表示使用该应用专属坐标（OWN_MODE），
     *                false 表示跟随宿主全局坐标（GLOBAL_MODE）。
     *                宿主只推一份全局坐标时用 GLOBAL_MODE 即可。
     */
    fun enableFor(packageName: String, ownMode: Boolean = false) {
        val mode = if (ownMode) BLocationManager.OWN_MODE else BLocationManager.GLOBAL_MODE
        runCatching {
            BLocationManager.get().setPattern(USER_ID, packageName, mode)
        }.onFailure { LogBus.warn("container enableFor failed: $packageName") }
    }

    /**
     * 批量开启所有容器应用的模拟，返回成功开启的数量。
     *
     * 只对已导入的应用逐个设置 GLOBAL 模式：容器内未导入的应用本来就不存在，
     * 不需要也不应该预先写入配置，否则容器配置里会积累大量无主条目。
     */
    fun enableAll(): Int = installedPackages().count { pkg ->
        runCatching {
            BLocationManager.get().setPattern(USER_ID, pkg, BLocationManager.GLOBAL_MODE)
            true
        }.getOrDefault(false)
    }

    /** 关闭所有容器应用的模拟，恢复真实定位。 */
    fun disableAll() {
        installedPackages().forEach { pkg ->
            runCatching {
                BLocationManager.get().setPattern(USER_ID, pkg, BLocationManager.CLOSE_MODE)
            }
        }
    }

    /** 读取单个应用当前生效的模式，用于诊断展示。 */
    fun patternOf(packageName: String): Int = runCatching {
        BLocationManager.get().getPattern(USER_ID, packageName)
    }.getOrDefault(BLocationManager.CLOSE_MODE)

    /**
     * 探测容器定位代理是否可读写。
     *
     * 用一个不存在的占位包名做只读查询：`BLocationManager` 内部出错时会
     * 静默返回 CLOSE_MODE，因此不能靠返回值判断，必须看 Binder 调用本身
     * 是否抛异常。成功（哪怕返回 CLOSE_MODE）即说明代理通道通畅。
     */
    fun probeLocationProxy(): Boolean {
        if (!available) return false
        return runCatching {
            BLocationManager.get().getPattern(USER_ID, HOST_PROBE_PACKAGE)
            true
        }.getOrDefault(false)
    }

    /**
     * 推送模拟坐标到容器定位代理服务。
     *
     * @param satellites 可见卫星数，导航类应用会读取该字段渲染信号图
     * @param provider   定位提供者，默认 gps；推网络定位时传 network
     * @return 是否成功写入；失败时清理定位服务缓存，下一 tick 自动重连
     */
    fun pushLocation(
        point: GeoPoint,
        accuracyMeters: Float,
        altitude: Double,
        speedMps: Float,
        bearing: Float,
        coordSystem: CoordSystem,
        satellites: Int = DEFAULT_SATELLITES,
        provider: String = BLocation.PROVIDER_GPS,
    ): Boolean {
        if (!available) return false
        return runCatching {
            BLocationManager.get().setGlobalLocation(
                buildLocation(point, accuracyMeters, altitude, speedMps, bearing, coordSystem, satellites, provider),
            )
        }.getOrElse {
            LogBus.warn("container push failed: ${it.javaClass.simpleName}")
            false
        }.also { ok ->
            if (!ok) clearLocationServiceCache()
        }
    }

    /**
     * 推送某个应用的专属坐标（OWN 模式）。
     *
     * 与 [pushLocation] 的区别是作用范围：写入后只有该应用（且模式为 OWN）
     * 会读到这份坐标，其他容器应用仍使用全局坐标。
     */
    fun pushAppLocation(
        packageName: String,
        point: GeoPoint,
        accuracyMeters: Float,
        altitude: Double,
        speedMps: Float,
        bearing: Float,
        coordSystem: CoordSystem,
        satellites: Int = DEFAULT_SATELLITES,
        provider: String = BLocation.PROVIDER_GPS,
    ): Boolean {
        if (!available) return false
        return runCatching {
            BLocationManager.get().setLocation(
                USER_ID,
                packageName,
                buildLocation(point, accuracyMeters, altitude, speedMps, bearing, coordSystem, satellites, provider),
            )
            true
        }.getOrElse {
            LogBus.warn("container push app location failed: $packageName (${it.javaClass.simpleName})")
            false
        }.also { ok ->
            if (!ok) clearLocationServiceCache()
        }
    }

    private fun buildLocation(
        point: GeoPoint,
        accuracyMeters: Float,
        altitude: Double,
        speedMps: Float,
        bearing: Float,
        coordSystem: CoordSystem,
        satellites: Int,
        provider: String,
    ): BLocation {
        val target = CoordTransform.transform(point, CoordSystem.GCJ02, coordSystem)
        return BLocation(target.lat, target.lng).apply {
            setAccuracy(accuracyMeters)
            setAltitude(altitude)
            setSpeed(speedMps.coerceAtLeast(0f))
            setBearing(bearing)
            // 细分精度由水平精度推导，避免出现 0 精度导致 SDK 丢弃结果
            setVerticalAccuracy(accuracyMeters * 2f)
            setSpeedAccuracy(0.5f)
            setBearingAccuracy(5f)
            setSatellites(satellites)
            setProvider(provider)
        }
    }

    /** 清掉可能失效的 :black Binder，下一 tick 由调用方自动重连。 */
    private fun clearLocationServiceCache() {
        runCatching { BlackBoxCore.get().clearServiceCache(ServiceManager.LOCATION_MANAGER) }
        runCatching { BLocationManager.get().clearServiceCache() }
    }

    /**
     * 推送基站指纹到容器。
     *
     * 容器应用读取 {@code TelephonyManager#getCellLocation} /
     * {@code getAllCellInfo} 时会拿到这份数据。写入主基站后，
     * 服务端会自动推导出邻区列表，无需重复推送。
     */
    fun pushCell(cell: BCell): Boolean {
        if (!available) return false
        return runCatching {
            BLocationManager.get().setGlobalCell(cell)
            true
        }.getOrElse {
            LogBus.warn("container push cell failed: ${it.javaClass.simpleName}")
            false
        }
    }

    /** 清空容器基站数据，避免关闭模拟后仍残留伪造基站。 */
    fun clearCell(): Boolean {
        if (!available) return false
        return runCatching {
            BLocationManager.get().setGlobalCell(null)
            true
        }.getOrDefault(false)
    }

    /** 可见卫星数默认值，与真实城市环境的中位值接近。 */
    const val DEFAULT_SATELLITES = 12

    /** 探测定位代理时使用的占位包名；读取模式不会写入任何配置。 */
    private const val HOST_PROBE_PACKAGE = "com.gpsproxy.dev.probe"
}
