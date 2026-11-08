package com.gpsproxy.dev.core.i18n.text

/** 引导、权限、引擎检测、设备不支持与启动闪屏的文案。 */
interface OnboardingTexts {
    val appTitle: String
    val tagline: String
    val boundarySection: String
    val boundaryTestOnly: String
    val boundaryChannels: String
    val boundaryScope: String
    val boundaryLiability: String
    val consentLabel: String
    val agreeContinue: String
    val declineExit: String
    fun versionLine(version: String): String

    val permissionsTitle: String
    fun permissionsReady(done: Int, total: Int): String
    val hintNeedMockApp: String
    val hintNeedLocation: String
    val hintNeedOverlay: String
    val hintAllReady: String
    val permLocationTitle: String
    val permLocationSubtitle: String
    val permNotificationTitle: String
    val permNotificationSubtitle: String
    val permOverlayTitle: String
    val permOverlaySubtitle: String
    val permMockAppTitle: String
    val permMockAppSubtitle: String
    val permBatteryTitle: String
    val permBatterySubtitle: String
    val privacyBanner: String
    val refreshStatus: String
    val mockAppDialogTitle: String
    val mockAppSteps: List<String>
    val mockAppFootnote: String
    val goDeveloperOptions: String
    val granted: String
    val goGrant: String
    val locationGranted: String
    val locationDenied: String

    val engineTitle: String
    val engineInitializing: String
    val engineReady: String
    val engineSubtitle: String
    val containerNotReadyTitle: String
    val containerNotReadyText: String
    val checkContainer: String
    val enterHome: String
    val viewUnsupportedReason: String
    val recheck: String

    val deviceCheckTitle: String
    val unsupportedTitle: String
    val unsupportedSubtitle: String
    val unsupportedFallbackIssue: String
    fun errorCodeLine(code: String): String
    val unsupportedMessage: String
    fun currentEnv(apiLevel: Int, abiLabel: String): String
    val viewSupportedDevices: String
    val supportScopeTitle: String
    val supportScopeText: String
    val gotIt: String

    val issueNo64Bit: String
    val issueHarmony: String
    val unknownAbi: String

    val splashTagline: String
    val mapMarkerLabel: String
}