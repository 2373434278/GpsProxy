package com.gpsproxy.dev.core.i18n.text

/** 首页与悬浮面板的状态、按钮与指标文案。 */
interface HomeTexts {
    val overlayTitle: String
    val overlayBadge: String
    val favoritePoint: String
    val repick: String
    val resumeInjection: String
    val pauseInjection: String
    val stopSimulation: String
    val startSimulation: String
    val walkSpeed: String
    val walkHintHold: String
    val walkHintDrag: String
    val overlayContentDescription: String

    val statusNotStarted: String
    val statusPaused: String
    val statusRouteCompleted: String
    val statusChannelMiss: String
    val statusRouteActive: String
    val statusWalking: String
    val statusActive: String

    val mapWatermarkHome: String
    val mapWatermarkDefault: String
    val mapZoomIn: String
    val mapZoomOut: String
    val mapRecenter: String
    val searchHint: String
    val permissions: String
    val quickFavorites: String
    val quickRoute: String
    val quickApps: String
    val quickSettings: String
    val externalMap: String
    val copyCoords: String
    val favorite: String
    fun blurChip(radiusMeters: Int): String
    fun blurDetail(radiusMeters: Int, pickedCoords: String): String
    fun autoStopAt(time: String): String
    val metricAccuracy: String
    val metricSpeed: String
    val metricAltitude: String
    val metricChannels: String
    val viewRoute: String
    val pickOnMap: String

    val blockContainerInitTitle: String
    val blockContainerInitMessage: String
    val blockRetryTitle: String
    val blockRetryMessage: String
    val blockContainerAppsTitle: String
    val blockContainerAppsMessage: String
    val blockPermissionsTitle: String
    val blockPermissionsMessage: String
    val blockNoneTitle: String
    val blockNoneMessage: String
    val blockConfirmImportApps: String
    val blockConfirmContainerInit: String
    val blockConfirmDeveloperOptions: String
    val blockConfirmPermissions: String
    val blockConfirmOk: String
    val blockDismissContainer: String
    val blockDismissLater: String
}