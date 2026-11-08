package com.gpsproxy.dev.core.i18n.text

/** 各 ViewModel/用例产生的瞬时提示文案。 */
interface MessageTexts {
    val simulationStopped: String
    val injectionPaused: String
    val injectionResumed: String
    val noMapApp: String
    val coordsCopied: String
    val addedToFavorites: String
    fun favoriteName(coords: String): String
    fun startSuccess(channel: String): String
    val logsCleared: String
    fun diagnosticsExported(fileName: String): String

    val autoStopped: String
    val routeStartPointSet: String
    val routeIncomplete: String
    val routeFavorited: String
    val routeSaveFailed: String
    fun trackImported(points: Int): String
    val routeNeedRoamCenter: String
    val routeNeedGpx: String
    val routeNeedEndpoints: String
    val routeAlreadyRunning: String
    val routeStarted: String
    fun routeLoaded(name: String): String
    val routeUnfavorited: String
    val routeFavoriteAdded: String
    val routeUpdateFailed: String
    val routeRenamed: String
    val routeRenameFailed: String
    fun routeDeleted(name: String): String
    val routeDeleteFailed: String
    fun speedFactorChanged(factor: String): String
    val routeDirectionSwitched: String
    val routeReplayed: String
    val routeCannotReplay: String
    val noTrackToExport: String
    val gpxExported: String
    val defaultGpxName: String
}