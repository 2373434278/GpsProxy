package com.gpsproxy.dev.domain.container

import com.gpsproxy.dev.domain.simulation.port.LocationSample
import java.io.File

/**
 * 容器应用管理端口。
 *
 * 表现层只依赖这个接口：导入、启停、切换模式、查询占用、设置专属坐标。
 * 平台实现负责 BlackBox Binder 调用与瞬时故障自愈。
 */
interface ContainerRepository {

    /** 返回 null 表示容器服务暂时不可达，调用方应保留上一次结果。 */
    suspend fun listApps(): List<ContainerApp>?

    suspend fun setLocationMode(packageName: String, mode: LocationMode): Boolean

    suspend fun importInstalled(packageName: String): ContainerImportResult

    suspend fun importApk(apk: File): ContainerImportResult

    fun launch(packageName: String): Boolean

    suspend fun uninstall(packageName: String): Boolean

    suspend fun sandboxSizeBytes(): Long

    /** 清空容器内所有应用的模拟状态与基站数据。 */
    suspend fun resetAllLocations(): Int

    /** 写入某个应用的专属坐标，并把它切到 OWN 模式。 */
    suspend fun setAppLocation(packageName: String, sample: LocationSample): Boolean

    /** 取消专属坐标，应用回到"跟随全局坐标"。 */
    suspend fun clearAppLocation(packageName: String): Boolean
}
