package com.gpsproxy.dev.core.i18n.text

/** 目标应用、容器应用与容器引擎页的文案。 */
interface ContainerTexts {
    val targetAppsTitle: String
    val refresh: String
    val commonAppsTitle: String
    val commonAppsBanner: String
    val filterPlaceholder: String
    val noLaunchableAppsTitle: String
    val noLaunchableAppsMessage: String
    val sectionCommon: String
    fun sectionAllApps(count: Int): String
    val systemBadge: String
    fun openApp(label: String): String
    val removeCommon: String
    val addCommon: String
    val removedCommon: String
    val addedCommon: String
    val cannotLaunchApp: String

    val containerAppsTitle: String
    val engineUnavailableTitle: String
    val engineUnavailableText: String
    val goEngineCheck: String
    val readsMockTitle: String
    val readsMockText: String
    fun sandboxUsage(megabytes: String): String
    val processing: String
    val importInstalled: String
    val importApk: String
    val restoreAll: String
    val loadAppsFailedTitle: String
    val loadAppsFailedMessage: String
    val emptyContainerTitle: String
    val emptyContainerMessage: String
    fun containerAppsSection(count: Int): String
    val cellSynced: String
    fun locationModeLabel(mode: String): String
    val ownBadge: String
    val useCurrentPoint: String
    val resumeFollow: String
    fun launchInContainer(label: String): String
    fun uninstallFromContainer(label: String): String
    fun importApp(label: String): String
    val unreadableFile: String
    val readFileFailed: String
    val cannotLaunchInContainer: String
    val uninstalled: String
    val uninstallFailed: String
    fun modeChanged(packageLabel: String, modeLabel: String): String
    val switchFailed: String
    val pickFirst: String
    fun ownSet(packageName: String): String
    val ownSetFailed: String
    fun ownCleared(packageName: String): String
    val ownClearFailed: String
    val restored: String
    fun imported(packageName: String): String
    val unknownReason: String
    val engineUnavailable: String
    val appPackageNotFound: String
    val apkFileMissing: String
    val importSucceeded: String
    val importFailed: String
    val importFailedConnection: String

    val initTitle: String
    val initInitializing: String
    val initReady: String
    val initUnavailable: String
    val initSubtitle: String
    val preparing: String
    fun checksDone(done: Int, total: Int): String
    val experimentalTitle: String
    val experimentalText: String
    val manageApps: String
    val recheck: String
}