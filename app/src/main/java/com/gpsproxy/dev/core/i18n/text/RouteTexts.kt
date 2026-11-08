package com.gpsproxy.dev.core.i18n.text

/** 路线配置、巡航运行与路线库的文案。 */
interface RouteTexts {
    fun travelModeLabel(mode: String): String
    fun routeKindLabel(kind: String): String
    fun routeSourceLabel(source: String): String

    /** 路网规划失败回退到内置曲线时的提示，[reason] 为上游原因。 */
    fun routeDegraded(reason: String): String

    /** [seconds] 的时长文案，例如「2 分钟」「1 小时 1 分」。 */
    fun duration(seconds: Int): String

    fun routeAddressRoam(radiusMeters: Int): String
    fun routeAddressImported(points: Int): String
    fun routeAddressPointToPoint(modeLabel: String): String
    fun manualNudge(coords: String): String

    fun savedNameRoam(radiusMeters: Int): String
    fun savedNameImported(points: Int): String
    fun savedNameEndpoints(start: String, end: String): String
    fun endpointSummary(start: String, end: String, waypointCount: Int): String

    val watermarkRoam: String
    fun watermarkImported(points: Int): String
    val watermarkRoute: String
    fun startLocation(label: String): String
    val addWaypoint: String
    fun waypointCount(count: Int): String
    val chooseEnd: String
    val routeEnd: String
    val cruiseSpeed: String
    fun roamCenter(label: String): String
    val roamRadius: String
    val roamSpeed: String
    val roamHint: String
    val importedTrack: String
    fun importedTrackLine(name: String, points: Int): String
    fun importedEndpoints(startCoords: String, endCoords: String): String
    val chooseGpx: String
    val gpxHint: String
    val runningBlockedConfig: String
    val needRoamCenter: String
    val needGpx: String
    val needEnd: String
    fun preview(distance: String, duration: String, points: Int): String
    val favoriteRoute: String
    val startRoute: String
    val loopRoute: String
    val loopRouteSubtitle: String
    val roundTrip: String
    val roundTripSubtitle: String
    val tapToEdit: String
    fun waypointsDialogTitle(count: Int, max: Int): String
    val moveUp: String
    val moveDown: String
    val editWaypoint: String
    val deleteWaypoint: String
    val noWaypoints: String
    val limitReached: String
    val addWaypointButton: String
    val done: String

    val statusError: String
    val statusCompleted: String
    val statusPaused: String
    fun statusRunning(speedKmh: Int, factor: String): String
    val statusIdle: String
    fun watermarkRunning(sourceLabel: String): String
    val genericTrack: String
    fun stopAt(time: String): String
    val exportGpx: String
    val backHome: String
    fun walked(distance: String): String
    val arrived: String
    fun remaining(duration: String): String
    val dragToSeek: String
    val metricPoints: String
    val metricBearing: String
    val metricAccuracy: String
    val speedFactor: String
    val reverse: String
    val resume: String
    val pause: String
    val stop: String
    val replay: String
    fun footer(total: String, index: Int, totalPoints: Int): String
    fun autoStopSuffix(time: String): String

    val libraryTitle: String
    val libraryStart: String
    val libraryEnd: String
    val waypointLabel: String
    val emptyTitle: String
    val emptyMessage: String
    fun favoritesSection(count: Int): String
    fun recentsSection(count: Int): String
    fun libraryHint(maxRecent: Int, maxFavorites: Int): String
    val deleteTitle: String
    fun deleteMessage(name: String): String
    val delete: String
    val favorite: String
    val unfavorite: String
    val rename: String
    fun summaryRoam(center: String, radiusMeters: Int, speedKmh: Int): String
    fun summaryImported(points: Int, speedKmh: Int): String
    fun summaryEndpoints(summary: String, speedKmh: Int): String
    val summaryLoopSuffix: String
    val summaryRoundTripSuffix: String
    val renameTitle: String
    val renameMessage: String
    val namePlaceholder: String
    val save: String
}