package com.gpsproxy.dev.platform.location

import android.content.Context
import android.annotation.SuppressLint
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.geo.CoordTransform
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.core.log.LogBus
import com.gpsproxy.dev.domain.simulation.model.ChannelId
import com.gpsproxy.dev.domain.simulation.port.InstallReport
import com.gpsproxy.dev.domain.simulation.port.LocationSample
import com.gpsproxy.dev.domain.simulation.port.PushReport
import com.gpsproxy.dev.domain.simulation.port.SystemMockGateway

/**
 * 官方模拟位置通道的 Android 适配器。
 *
 * 通过 `LocationManager.addTestProvider` / `setTestProviderLocation` 写入坐标。
 * 系统会给这些值打上 `isMock = true` 标记；本实现**刻意不做隐藏**：
 * 这是产品的能力边界——不重打包、不修改签名、不绕过系统检测。
 *
 * 与容器通道的区别：
 * - 本通道改动的是宿主设备真实定位，对全部应用生效；
 * - 容器通道只影响导入沙箱的容器应用，不触碰宿主。
 *
 * 适配器只负责「把领域载荷翻译成平台对象」与「把平台异常翻译成报告」，
 * 是否可用、何时安装、失败几次算致命，全部由领域层决定。
 */
class AndroidSystemMockGateway(context: Context) : SystemMockGateway {

    private val locationManager: LocationManager? =
        context.getSystemService(LocationManager::class.java)

    /** 已成功挂载的测试提供者。重复挂载会被系统拒绝，因此需要自己记录。 */
    private val installed = linkedSetOf<String>()

    override val installedProviderNames: List<String>
        get() = installed.toList()

    /**
     * 候选顺序 GPS -> Network -> fused：前两个是必须项，
     * fused 自 Android 12 起才以独立提供者形式暴露，缺失不影响主体能力。
     */
    private fun candidateProviders(): List<String> = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(PROVIDER_FUSED)
    }

    @SuppressLint("WrongConstant")
    override fun install(): InstallReport {
        val manager = locationManager
            ?: return InstallReport(
                failures = listOf(StringsHolder.text { log.locationManagerUnavailable }),
            )

        val failures = mutableListOf<String>()
        candidateProviders().forEach { provider ->
            if (installed.contains(provider)) return@forEach
            try {
                try {
                    // addTestProvider 在 API 31 之前只接受 Criteria 的常量；
                    // ProviderProperties 的同名常量值相同，但旧设备没有该类，
                    // 因此这里保留 Criteria 并显式抑制 lint 的常量类型检查。
                    manager.addTestProvider(
                        provider,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE,
                    )
                } catch (_: IllegalArgumentException) {
                    // 提供者可能在上个进程中残留：重新启用即可。
                }
                manager.setTestProviderEnabled(provider, true)
                installed.add(provider)
                LogBus.info("mock provider ready: $provider")
            } catch (error: SecurityException) {
                failures.add(StringsHolder.text { log.providerDenied(provider) })
                LogBus.error("mock provider denied: $provider (${error.javaClass.simpleName})")
            } catch (error: Throwable) {
                failures.add("$provider: ${error.javaClass.simpleName}")
                LogBus.warn("mock provider unavailable: $provider (${error.javaClass.simpleName})")
            }
        }
        return InstallReport(
            installed = installed.mapNotNull(::channelOf),
            providerNames = installed.toList(),
            failures = failures,
        )
    }

    override fun push(sample: LocationSample): PushReport {
        val manager = locationManager
            ?: return PushReport(
                failures = listOf(StringsHolder.text { log.locationManagerUnavailable }),
            )
        if (installed.isEmpty()) {
            return PushReport(
                failures = listOf(StringsHolder.text { log.noTestProvider }),
            )
        }
        val target = CoordTransform.transform(sample.point, CoordSystem.GCJ02, sample.coordSystem)
        val successProviders = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val elapsed = SystemClock.elapsedRealtimeNanos()
        val satellites = sample.satellites.takeIf { it > 0 }
        installed.toList().forEach { provider ->
            try {
                val location = Location(provider).apply {
                    latitude = target.lat
                    longitude = target.lng
                    accuracy = sample.accuracyMeters.toFloat()
                    altitude = sample.altitude
                    speed = sample.speedMps.coerceAtLeast(0f)
                    bearing = sample.bearing
                    time = sample.wallMillis
                    elapsedRealtimeNanos = elapsed
                    // 与容器通道保持一致：部分 SDK 缺精度细分字段会丢弃结果。
                    verticalAccuracyMeters = sample.accuracyMeters * 2f
                    speedAccuracyMetersPerSecond = 1.0f
                    bearingAccuracyDegrees = 10.0f
                    // 容器通道把卫星数写进 extras；同一应用在两条通道下应读到相同字段。
                    if (satellites != null) {
                        extras = Bundle().apply {
                            putInt("satellites", satellites)
                            putInt("satellitesvalue", satellites)
                        }
                    }
                }
                manager.setTestProviderLocation(provider, location)
                successProviders.add(provider)
            } catch (error: Throwable) {
                failures.add("$provider: ${error.javaClass.simpleName}")
            }
        }
        return PushReport(
            hits = successProviders.mapNotNull(::channelOf),
            providerNames = successProviders,
            failures = failures,
        )
    }

    /** 卸载测试提供者，恢复真实定位。必须在停止模拟时调用，否则系统会保留假 provider。 */
    override fun teardown() {
        val manager = locationManager ?: return
        installed.toList().forEach { provider ->
            runCatching { manager.setTestProviderEnabled(provider, false) }
            runCatching { manager.removeTestProvider(provider) }
            LogBus.info("mock provider removed: $provider")
        }
        installed.clear()
    }

    private fun channelOf(provider: String): ChannelId? = when (provider) {
        LocationManager.GPS_PROVIDER -> ChannelId.GPS
        LocationManager.NETWORK_PROVIDER -> ChannelId.NETWORK
        PROVIDER_FUSED -> ChannelId.FUSED
        else -> null
    }

    private companion object {
        /** fused 提供者的字符串常量在部分 API 级别未公开，使用稳定值。 */
        const val PROVIDER_FUSED = "fused"
    }
}
