package com.gpsproxy.dev.core.i18n.text

/** 引擎自检与容器专项自检的步骤名与结果说明。 */
interface SelfCheckTexts {
    val engineVersionTitle: String
    fun engineVersionPending(apiLevel: Int): String
    fun engineSupported(apiLevel: Int, abiLabel: String): String
    val engineUnsupported: String
    val engineContainerTitle: String
    val engineContainerPending: String
    val engineContainerReady: String
    val engineContainerUnavailable: String
    val engineProxyTitle: String
    val engineProxyPending: String
    val engineProxyReady: String
    val engineProxyUnavailable: String
    val engineAppsTitle: String
    val engineAppsPending: String
    fun appsImported(count: Int): String
    val noAppsImported: String
    val engineMockTitle: String
    val engineMockPending: String
    val engineMockReady: String
    val engineMockUnavailable: String

    val containerRuntimeTitle: String
    val containerRuntimePending: String
    val containerRuntimeReady: String
    val containerRuntimeFailed: String
    val abiTitle: String
    val abiPending: String
    fun abiDetail(abiLabel: String): String
    val abiMissing: String
    val hookTitle: String
    val hookPending: String
    val hookReady: String
    val hookFailed: String
    val binderTitle: String
    val binderPending: String
    val binderReady: String
    val binderFailed: String
    val locationProxyTitle: String
    val locationProxyPending: String
    val locationProxyReady: String
    val locationProxyFailed: String

    fun exception(error: String): String
}