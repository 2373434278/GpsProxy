package com.gpsproxy.dev.platform.selfcheck

import android.content.Context
import android.os.Build
import com.gpsproxy.dev.GpsProxyApp
import com.gpsproxy.dev.container.ContainerEngine
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.core.device.DeviceCapabilityChecker
import com.gpsproxy.dev.core.device.PermissionUtils
import com.gpsproxy.dev.domain.selfcheck.SelfCheckDefinition
import com.gpsproxy.dev.domain.selfcheck.SelfCheckResult
import com.gpsproxy.dev.domain.simulation.port.ContainerGateway
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.fake.frameworks.BLocationManager
import top.niunaijun.blackbox.fake.frameworks.BPackageManager

/**
 * 把平台探测包装成自检项。
 *
 * 这里只做"读事实"：版本、ABI、容器运行时、Binder 连通性、授权状态。
 * 通过/不通过如何展示、是否阻断进入首页，由用例与 UI 决定。
 */
class AndroidSelfCheckFactory(
    private val context: Context,
    private val container: ContainerGateway,
    private val strings: () -> Strings = { StringsHolder.current },
) {

    /** 首次启动的引擎自检（5 项）。 */
    fun engineChecks(): List<SelfCheckDefinition> {
        val texts = strings().selfCheck
        return listOf(
            SelfCheckDefinition(
                title = texts.engineVersionTitle,
                pendingDetail = texts.engineVersionPending(Build.VERSION.SDK_INT),
            ) {
                val capability = DeviceCapabilityChecker.check()
                if (capability.supported) {
                    SelfCheckResult(
                        true,
                        texts.engineSupported(capability.apiLevel, capability.abiLabel),
                    )
                } else {
                    SelfCheckResult(false, texts.engineUnsupported)
                }
            },
            SelfCheckDefinition(
                title = texts.engineContainerTitle,
                pendingDetail = texts.engineContainerPending,
            ) {
                if (container.available) {
                    SelfCheckResult(true, texts.engineContainerReady)
                } else {
                    SelfCheckResult(false, texts.engineContainerUnavailable)
                }
            },
            SelfCheckDefinition(
                title = texts.engineProxyTitle,
                pendingDetail = texts.engineProxyPending,
            ) {
                if (container.probe()) {
                    SelfCheckResult(true, texts.engineProxyReady)
                } else {
                    SelfCheckResult(false, texts.engineProxyUnavailable)
                }
            },
            SelfCheckDefinition(
                title = texts.engineAppsTitle,
                pendingDetail = texts.engineAppsPending,
            ) {
                val count = runCatching { ContainerEngine.installedPackages().size }.getOrDefault(0)
                if (count > 0) {
                    SelfCheckResult(true, texts.appsImported(count))
                } else {
                    SelfCheckResult(false, texts.noAppsImported)
                }
            },
            SelfCheckDefinition(
                title = texts.engineMockTitle,
                pendingDetail = texts.engineMockPending,
            ) {
                if (PermissionUtils.isMockLocationAllowed(context)) {
                    SelfCheckResult(true, texts.engineMockReady)
                } else {
                    SelfCheckResult(false, texts.engineMockUnavailable)
                }
            },
        )
    }

    /** 容器专项自检（5 项），用于容器检测页。 */
    fun containerChecks(): List<SelfCheckDefinition> {
        val texts = strings().selfCheck
        val onboarding = strings().onboarding
        return listOf(
            SelfCheckDefinition(
                title = texts.containerRuntimeTitle,
                pendingDetail = texts.containerRuntimePending,
            ) {
                if (GpsProxyApp.containerReady) {
                    SelfCheckResult(true, texts.containerRuntimeReady)
                } else {
                    SelfCheckResult(false, texts.containerRuntimeFailed)
                }
            },
            SelfCheckDefinition(
                title = texts.abiTitle,
                pendingDetail = texts.abiPending,
            ) {
                if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
                    SelfCheckResult(
                        true,
                        texts.abiDetail(Build.SUPPORTED_ABIS.firstOrNull() ?: onboarding.unknownAbi),
                    )
                } else {
                    SelfCheckResult(false, texts.abiMissing)
                }
            },
            SelfCheckDefinition(
                title = texts.hookTitle,
                pendingDetail = texts.hookPending,
            ) {
                val ready = GpsProxyApp.containerReady &&
                    runCatching { BlackBoxCore.get().isServicesAvailable }.getOrDefault(false)
                if (ready) {
                    SelfCheckResult(true, texts.hookReady)
                } else {
                    SelfCheckResult(false, texts.hookFailed)
                }
            },
            SelfCheckDefinition(
                title = texts.binderTitle,
                pendingDetail = texts.binderPending,
            ) {
                val ok = runCatching {
                    BPackageManager.get().getInstalledPackages(0, ContainerEngine.USER_ID)
                }.isSuccess
                if (ok) {
                    SelfCheckResult(true, texts.binderReady)
                } else {
                    SelfCheckResult(false, texts.binderFailed)
                }
            },
            SelfCheckDefinition(
                title = texts.locationProxyTitle,
                pendingDetail = texts.locationProxyPending,
            ) {
                // 定位服务单独探测：包管理可用不代表定位代理已注册
                val ok = runCatching {
                    BLocationManager.get().getPattern(ContainerEngine.USER_ID, HOST_PROBE_PACKAGE)
                }.isSuccess && container.available
                if (ok) {
                    SelfCheckResult(true, texts.locationProxyReady)
                } else {
                    SelfCheckResult(false, texts.locationProxyFailed)
                }
            },
        )
    }

    private companion object {
        /** 只读探测用的占位包名；读取模式不会写入任何配置。 */
        const val HOST_PROBE_PACKAGE = "com.gpsproxy.dev.probe"
    }
}
