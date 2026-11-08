package com.gpsproxy.dev.container

import android.content.Context
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.core.log.LogBus
import com.gpsproxy.dev.domain.container.ContainerApp
import com.gpsproxy.dev.domain.container.ContainerImportResult
import com.gpsproxy.dev.domain.container.ContainerRepository
import com.gpsproxy.dev.domain.container.LocationMode
import com.gpsproxy.dev.domain.simulation.port.LocationSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.BEnvironment
import top.niunaijun.blackbox.entity.pm.InstallResult
import top.niunaijun.blackbox.fake.frameworks.BLocationManager
import top.niunaijun.blackbox.fake.frameworks.BPackageManager
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * 容器应用仓库：封装 BlackBox 的包管理与定位代理调用。
 *
 * 所有 Binder 调用都放在 IO 线程并做了异常隔离。容器服务在宿主启动初期
 * 可能短暂不可达（:black 进程尚未就绪），这类失败会用「重置服务缓存 + 重试一次」
 * 的方式自愈，而不是直接向 UI 报错。
 */
class ContainerAppsRepository(private val context: Context) : ContainerRepository {

    /** 每次列表查询的代数，用于诊断日志里区分「第几次刷新拿到的结果」。 */
    private val listGeneration = AtomicInteger(0)

    /** 返回 null 表示容器服务暂时不可达，调用方应保留上一次结果 */
    override suspend fun listApps(): List<ContainerApp>? = withContext(Dispatchers.IO) {
        // 不可达与"没有应用"必须区分：前者应提示重试，后者才引导导入。
        if (!ContainerEngine.available) return@withContext null
        val generation = listGeneration.incrementAndGet()
        val first = queryApps()
        if (first == null) {
            LogBus.warn("container list retry after service reset (gen=$generation)")
            Thread.sleep(300)
            ContainerEngine.resetPackageService()
            return@withContext queryApps()
        }
        // :black 正在重启时可能返回空清单而不是抛异常；空结果再确认一次，
        // 避免把"暂时读不到"误判成"容器内没有应用"。
        if (first.isNotEmpty()) return@withContext first
        LogBus.warn("container list empty; re-check after service reset (gen=$generation)")
        Thread.sleep(300)
        ContainerEngine.resetPackageService()
        queryApps() ?: return@withContext null
    }

    /**
     * 查询容器内应用清单。
     *
     * 每个应用的定位模式与基站状态分别查一次，用于在列表里直接展示
     * 「该应用是否已开启模拟」，避免用户还要逐个点进去确认。
     */
    private fun queryApps(): List<ContainerApp>? = runCatching {
        BPackageManager.get().getInstalledPackages(0, ContainerEngine.USER_ID)
            .mapNotNull { info -> info.packageName?.let { pkg -> pkg to info.applicationInfo } }
            .map { (pkg, info) ->
                ContainerApp(
                    packageName = pkg,
                    label = runCatching { info?.loadLabel(context.packageManager)?.toString() }
                        .getOrNull()?.ifBlank { null } ?: hostLabel(pkg) ?: pkg,
                    locationMode = LocationMode.fromRaw(ContainerEngine.patternOf(pkg)),
                    hasCellFingerprint = runCatching {
                        BLocationManager.get().getCell(ContainerEngine.USER_ID, pkg) != null
                    }.getOrDefault(false),
                )
            }
            .sortedBy { it.label.lowercase() }
    }.getOrNull()

    /** 切换单个容器应用的定位模拟模式。 */
    override suspend fun setLocationMode(packageName: String, mode: LocationMode): Boolean =
        withContext(Dispatchers.IO) {
            if (!ContainerEngine.available) return@withContext false
            runCatching {
                BLocationManager.get()
                    .setPattern(ContainerEngine.USER_ID, packageName, mode.raw)
                LogBus.info("container mode $packageName -> ${mode.name}")
                true
            }.getOrElse {
                LogBus.warn("container mode change failed: $packageName (${it.javaClass.simpleName})")
                false
            }
        }

    override suspend fun importInstalled(packageName: String): ContainerImportResult =
        withContext(Dispatchers.IO) {
            if (!ContainerEngine.available) {
                return@withContext ContainerImportResult(
                    false,
                    message = StringsHolder.text { container.engineUnavailable },
                )
            }
            val sourceDir = runCatching {
                context.packageManager.getApplicationInfo(packageName, 0).sourceDir
            }.getOrNull() ?: return@withContext ContainerImportResult(
                false,
                packageName,
                StringsHolder.text { container.appPackageNotFound },
            )
            importApk(File(sourceDir))
        }

    override suspend fun importApk(apk: File): ContainerImportResult = withContext(Dispatchers.IO) {
        if (!ContainerEngine.available) {
            return@withContext ContainerImportResult(
                    false,
                    message = StringsHolder.text { container.engineUnavailable },
                )
        }
        if (!apk.exists()) {
            return@withContext ContainerImportResult(
                false,
                message = StringsHolder.text { container.apkFileMissing },
            )
        }
        attemptInstall(apk)?.let { result ->
            return@withContext if (result.success) {
                LogBus.info("container import ok: ${result.packageName}")
                ContainerImportResult(
                    true,
                    result.packageName,
                    StringsHolder.text { container.importSucceeded },
                )
            } else {
                LogBus.warn("container import failed: ${result.msg}")
                ContainerImportResult(
                    false,
                    result.packageName,
                    result.msg ?: StringsHolder.text { container.importFailed },
                )
            }
        }
        // 主进程到 :black 的 Binder 可能瞬时失效，清缓存后重试一次
        LogBus.warn("container import retry after service reset")
        Thread.sleep(300)
        ContainerEngine.resetPackageService()
        val retry = attemptInstall(apk)
        if (retry?.success == true) {
            LogBus.info("container import ok on retry: ${retry.packageName}")
            ContainerImportResult(
                true,
                retry.packageName,
                StringsHolder.text { container.importSucceeded },
            )
        } else {
            LogBus.error("container import error: binder unavailable")
            ContainerImportResult(
                false,
                message = StringsHolder.text { container.importFailedConnection },
            )
        }
    }

    private fun attemptInstall(apk: File): InstallResult? = runCatching {
        BlackBoxCore.get().installPackageAsUser(apk, ContainerEngine.USER_ID)
    }.getOrNull()

    override fun launch(packageName: String): Boolean {
        if (!ContainerEngine.available) return false
        return runCatching { BlackBoxCore.get().launchApk(packageName, ContainerEngine.USER_ID) }
            .getOrDefault(false)
    }

    override suspend fun uninstall(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!ContainerEngine.available) return@withContext false
        runCatching {
            BlackBoxCore.get().uninstallPackageAsUser(packageName, ContainerEngine.USER_ID)
            LogBus.info("container uninstall: $packageName")
            true
        }.getOrDefault(false)
    }

    /** 统计容器沙箱占用的磁盘空间。 */
    override suspend fun sandboxSizeBytes(): Long = withContext(Dispatchers.IO) {
        runCatching {
            BEnvironment.getVirtualRoot()
                ?.walkTopDown()
                ?.filter { it.isFile }
                ?.sumOf { it.length() } ?: 0L
        }.getOrDefault(0L)
    }

    /** 清空容器内所有应用的模拟状态与基站数据，用于「一键还原」。 */
    override suspend fun resetAllLocations(): Int = withContext(Dispatchers.IO) {
        if (!ContainerEngine.available) return@withContext 0
        ContainerEngine.disableAll()
        ContainerEngine.clearCell()
        (listApps()?.size) ?: 0
    }

    /**
     * 写入某个应用的专属坐标（OWN 模式）。
     *
     * 顺序是"先切模式、再写坐标"：若顺序反了，应用在两次调用之间会短暂读到
     * 全局坐标，导航类应用会把这段跳变当成一次瞬移。
     */
    override suspend fun setAppLocation(packageName: String, sample: LocationSample): Boolean =
        withContext(Dispatchers.IO) {
            if (!ContainerEngine.available) return@withContext false
            val modeSet = runCatching {
                BLocationManager.get().setPattern(ContainerEngine.USER_ID, packageName, LocationMode.OWN.raw)
                true
            }.getOrDefault(false)
            if (!modeSet) return@withContext false
            val pushed = ContainerEngine.pushAppLocation(
                packageName = packageName,
                point = sample.point,
                accuracyMeters = sample.accuracyMeters.toFloat(),
                altitude = sample.altitude,
                speedMps = sample.speedMps,
                bearing = sample.bearing,
                coordSystem = sample.coordSystem,
                satellites = sample.satellites,
            )
            LogBus.info("container own location $packageName -> ${sample.point.formatted()} pushed=$pushed")
            pushed
        }

    /** 取消专属坐标：回到跟随宿主全局坐标，而不是直接关闭模拟。 */
    override suspend fun clearAppLocation(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!ContainerEngine.available) return@withContext false
            runCatching {
                // 取消专属 = 回到真实定位；若仍想跟随全局坐标，用户可以点"跟随"。
                BLocationManager.get().setPattern(
                    ContainerEngine.USER_ID,
                    packageName,
                    LocationMode.CLOSE.raw,
                )
                LogBus.info("container own location cleared: $packageName")
                true
            }.getOrDefault(false)
        }

    private fun hostLabel(packageName: String): String? = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrNull()
}
