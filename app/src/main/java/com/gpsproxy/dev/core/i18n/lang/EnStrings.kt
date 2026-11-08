package com.gpsproxy.dev.core.i18n.lang

import com.gpsproxy.dev.core.i18n.AppLanguage
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.text.ChannelTexts
import com.gpsproxy.dev.core.i18n.text.CommonTexts
import com.gpsproxy.dev.core.i18n.text.ContainerTexts
import com.gpsproxy.dev.core.i18n.text.ErrorTexts
import com.gpsproxy.dev.core.i18n.text.HomeTexts
import com.gpsproxy.dev.core.i18n.text.LogTexts
import com.gpsproxy.dev.core.i18n.text.MessageTexts
import com.gpsproxy.dev.core.i18n.text.NotificationTexts
import com.gpsproxy.dev.core.i18n.text.OnboardingTexts
import com.gpsproxy.dev.core.i18n.text.PlaceTexts
import com.gpsproxy.dev.core.i18n.text.RouteTexts
import com.gpsproxy.dev.core.i18n.text.SelfCheckTexts
import com.gpsproxy.dev.core.i18n.text.SettingsTexts

object EnStrings : Strings {
    override val language = AppLanguage.EN
    override val common: CommonTexts = EnCommon
    override val home: HomeTexts = EnHome
    override val settings: SettingsTexts = EnSettings
    override val route: RouteTexts = EnRoute
    override val error: ErrorTexts = EnError
    override val selfCheck: SelfCheckTexts = EnSelfCheck
    override val channel: ChannelTexts = EnChannel
    override val message: MessageTexts = EnMessage
    override val place: PlaceTexts = EnPlace
    override val container: ContainerTexts = EnContainer
    override val onboarding: OnboardingTexts = EnOnboarding
    override val notification: NotificationTexts = EnNotification
    override val log: LogTexts = EnLog
}

private object EnCommon : CommonTexts {
    override val errorDetailSeparator = ": "
    override val cancel = "Cancel"
    override val confirm = "OK"
    override val back = "Back"
    override val retry = "Retry"
    override val close = "Close"
}

private object EnSettings : SettingsTexts {
    override val pageTitle = "Settings"
    override val generalSection = "General"
    override val coordSystemSection = "Coordinate system"
    override val locationSection = "Location"
    override val payloadSection = "Location payload"
    override val sessionSection = "Session"
    override val injectionSection = "Injection"
    override val startupLogSection = "Startup & logs"
    override val aboutSection = "About"

    override val languageTitle = "App language"
    override val languageSubtitle =
        "Applies immediately; existing logs and generated names keep the language they were written in"
    override fun languageName(language: AppLanguage): String = when (language) {
        AppLanguage.SYSTEM -> "System"
        AppLanguage.ZH_HANS -> "简体中文"
        AppLanguage.EN -> "English"
    }

    override val coordSystemTitle = "Injected coordinate system"
    override val coordSystemSubtitle = "Use GCJ-02 for apps in mainland China; WGS-84 for apps elsewhere"
    override fun coordSystemDescription(system: String): String = when (system) {
        "GCJ02" -> "GCJ-02 encrypted coordinates, standard for apps in mainland China"
        "WGS84" -> "Raw GPS coordinates, standard for apps outside mainland China"
        "BD09" -> "Baidu's secondary offset coordinates"
        else -> system
    }

    override val accuracyTitle = "Simulated accuracy"
    override val accuracySubtitle = "Written to Location.accuracy; too small is easily flagged"
    override val driftTitle = "Random drift"
    override val driftSubtitle = "Adds 0.5–2 m jitter at the stationary point for naturalness only"
    override val blurTitle = "Location blur"
    override val blurSubtitle = "Randomly shifts the real point within a radius so apps cannot recover it"
    override val blurRadiusTitle = "Blur radius"
    override val blurRadiusSubtitle = "Larger radii are harder to locate; too large crosses blocks"
    override val intervalTitle = "Stationary update interval"
    override val intervalSubtitle = "Affects battery use and track naturalness"

    override val reportSpeedTitle = "Reported speed"
    override val reportSpeedSubtitle = "Written to Location.speed when stationary; payload only, no movement"
    override val reportBearingTitle = "Reported bearing"
    override val reportBearingSubtitle = "Written to Location.bearing when stationary; 0 keeps the last bearing"
    override val altitudeTitle = "Altitude"
    override val altitudeSubtitle = "Base value for Location.altitude, still with ±1 m jitter"
    override val walkSpeedTitle = "Walk speed"
    override val walkSpeedSubtitle = "Maximum speed when the overlay joystick is pushed fully"

    override val autoStopTitle = "Session time limit"
    override val autoStopSubtitle =
        "Wall-clock based; pausing does not pause the timer; applies to the next start"
    override val unlimited = "Unlimited"
    override fun minutes(value: Int): String = if (value == 1) "1 minute" else "$value minutes"

    override val channelTitle = "Primary injection channel"
    override val channelSubtitle =
        "Container proxy is root-free with no isMock; system mock needs developer-options authorization"
    override fun channelName(channel: String): String = when (channel) {
        "CONTAINER" -> "Container proxy"
        "OFFICIAL" -> "System mock"
        else -> channel
    }

    override fun channelDescription(channel: String): String = when (channel) {
        "CONTAINER" -> "Root-free: proxies location inside the sandbox; no system mock, no isMock flag"
        "OFFICIAL" ->
            "Writes to the system test provider; requires developer-options authorization and marks coordinates as isMock"
        else -> ""
    }

    override val containerRuntimeTitle = "Container runtime"
    override val containerRuntimeReady =
        "Ready: coordinates go over Binder into the sandbox, where container apps read the mock location"
    override val containerRuntimeUnavailable =
        "Unavailable: retry on the container check page, or switch to the system mock channel"
    override val ready = "Ready"
    override val unavailable = "Unavailable"
    override val containerSpaceTitle = "Container space"
    override val containerSpaceSubtitle =
        "Import target apps to run and simulate location inside the isolated sandbox"
    override val overlayTitle = "System overlay ball"
    override val overlaySubtitle = "Shown when switching to other apps; pause/stop on the target app"

    override val lastSelfCheckTitle = "Last engine self-check"
    override val lastSelfCheckNever = "No complete self-check yet"
    override fun lastSelfCheckAt(time: String): String = "Last: $time"
    override val never = "Never"
    override val checked = "Checked"
    override val notificationDetailTitle = "Foreground notification content"
    override val notificationDetailSubtitle =
        "Android requires a foreground notification; this controls density, not visibility"
    override fun notificationDetailName(detail: String): String = when (detail) {
        "COMPACT" -> "Compact"
        "DETAILED" -> "Detailed"
        else -> detail
    }

    override fun notificationDetailDescription(detail: String): String = when (detail) {
        "COMPACT" -> "Coordinates and accuracy only; a quieter notification"
        "DETAILED" -> "Adds channel, coordinate system and blur radius"
        else -> ""
    }

    override val bootRestoreTitle = "Restore after boot"
    override val bootRestoreSubtitle =
        "Shows a single restore notification after reboot; does not start the foreground service (Android 12+ restriction)"
    override val logLevelTitle = "Log level"
    override val logLevelSubtitle = "Verbose records more channel and coordinate-transform details"
    override fun detailLevelName(level: String): String = when (level) {
        "NORMAL" -> "Normal"
        "VERBOSE" -> "Verbose"
        else -> level
    }

    override val clearLogsTitle = "Clear live logs"
    override val clearLogsSubtitle = "Clears the in-memory buffer only; nothing is uploaded"

    override val capabilityTitle = "Capability boundaries"
    override val capabilityText =
        "Container proxy channel: root-free; it hooks location APIs only inside the isolated sandbox, " +
            "does not write system mocks, adds no isMock flag, and never modifies host apps. " +
            "Scope is limited to imported apps inside the container. The system mock channel affects all host apps, " +
            "but coordinates carry an isMock flag and require developer-options authorization. " +
            "Target apps may still detect anomalies through multi-source consistency checks; evading that is out of scope."
    override val openDiagnostics = "Open runtime diagnostics"

    override val versionTitle = "Version"
    override fun versionSubtitle(version: String, buildType: String): String =
        "GpsProxy $version · $buildType build"
    override val poiSourceTitle = "POI data source"
    override val poiSourceLive = "Amap Web Service key configured; real POI search enabled"
    override val poiSourceBuiltin =
        "Built-in sample data; configure an Amap Web Service key in local.properties for real search"
    override val amap = "Amap"
    override val builtin = "Built-in samples"
    override val supportTitle = "Supported devices"
    override val supportSubtitle = "Android 8.1 (API 27) and above · arm64 devices"
    override val supportValue = "API 27+"
    override val privacy =
        "Location data is generated and stored on-device only; no network access except optional POI requests."

    override val accuracyDialogTitle = "Simulated accuracy"
    override val intervalDialogTitle = "Stationary update interval"
    override val blurRadiusDialogTitle = "Blur radius"
    override val logLevelDialogTitle = "Log level"
    override val reportSpeedDialogTitle = "Reported speed"
    override val reportBearingDialogTitle = "Reported bearing"
    override val altitudeDialogTitle = "Altitude"
    override val walkSpeedDialogTitle = "Walk speed"
    override val autoStopDialogTitle = "Session time limit"
    override fun intervalOption(millis: Int): String = when (millis) {
        500 -> "0.5 s"
        1000 -> "1 s"
        2000 -> "2 s"
        else -> "${millis / 1000} s"
    }

    override fun accuracyOption(meters: Int): String = "±$meters m"
    override fun kmh(value: Int): String = "$value km/h"
    override fun degrees(value: Int): String = "$value°"
    override fun meters(value: Int): String = "$value m"
}

private object EnHome : HomeTexts {
    override val overlayTitle = "Quick controls"
    override val overlayBadge = "Overlay"
    override val favoritePoint = "Save point"
    override val repick = "Pick again"
    override val resumeInjection = "Resume injection"
    override val pauseInjection = "Pause injection"
    override val stopSimulation = "Stop simulation"
    override val walkSpeed = "Walk speed"
    override val walkHintHold = "Hold to walk, release to stop"
    override val walkHintDrag = "Drag to fine-tune; holds after release"
    override val overlayContentDescription = "Quick controls"
    override val startSimulation = "Start simulation"

    override val statusNotStarted = "Not started"
    override val statusPaused = "Simulation paused"
    override val statusRouteCompleted = "Route completed"
    override val statusChannelMiss = "No channel hit"
    override val statusRouteActive = "Route active"
    override val statusWalking = "Walking"
    override val statusActive = "Simulation active"

    override val mapWatermarkHome = "Offline schematic map · Long-press to pick"
    override val mapWatermarkDefault = "Offline schematic map · GCJ-02"
    override val mapZoomIn = "Zoom in"
    override val mapZoomOut = "Zoom out"
    override val mapRecenter = "Back to marker"
    override val searchHint = "Search a place, or long-press the map to pick"
    override val permissions = "Permissions"
    override val quickFavorites = "Favorites & history"
    override val quickRoute = "Route simulation"
    override val quickApps = "Target apps"
    override val quickSettings = "Settings"
    override val externalMap = "External map"
    override val copyCoords = "Copy coordinates"
    override val favorite = "Favorite"
    override fun blurChip(radiusMeters: Int): String = "Blur ±${radiusMeters}m"
    override fun blurDetail(radiusMeters: Int, pickedCoords: String): String =
        "Blur ±$radiusMeters m · Picked $pickedCoords"
    override fun autoStopAt(time: String): String = "Auto-stop at $time"
    override val metricAccuracy = "Accuracy"
    override val metricSpeed = "Speed"
    override val metricAltitude = "Altitude"
    override val metricChannels = "Channels"
    override val viewRoute = "View route"
    override val pickOnMap = "Pick on map"

    override val blockContainerInitTitle = "Container runtime unavailable"
    override val blockContainerInitMessage =
        "The virtualization engine did not start with the app, or the container service process cannot be reached.\n\n" +
            "Re-run detection in the container space, or switch to the system Mock channel for now " +
            "(it affects all apps, and coordinates carry the isMock flag)."
    override val blockRetryTitle = "Container service temporarily unreachable"
    override val blockRetryMessage =
        "Could not read the app list inside the container: the container process may still be starting, or was frozen by the system.\n\n" +
            "Imported apps are not lost; tap \"Retry\" later. " +
            "If it keeps failing, open the container space to review the self-check results."
    override val blockContainerAppsTitle = "No container apps yet"
    override val blockContainerAppsMessage =
        "The container proxy is root-free, but only applies to apps imported into the container:\n\n" +
            "1. Import target apps from installed apps or an APK\n" +
            "2. Return home and tap \"Start simulation\"\n\n" +
            "Coordinates read by apps inside the container do not carry the isMock flag."
    override val blockPermissionsTitle = "System authorization required"
    override val blockPermissionsMessage =
        "1. Open Developer options\n" +
            "2. Choose \"Select mock location app\"\n" +
            "3. Select GpsProxy\n\n" +
            "Location permission is also required (strictly enforced when starting a foreground location service on Android 14+)."
    override val blockNoneTitle = "Cannot start right now"
    override val blockNoneMessage = "Please try again later."
    override val blockConfirmImportApps = "Import apps"
    override val blockConfirmContainerInit = "Open container space"
    override val blockConfirmDeveloperOptions = "Developer options"
    override val blockConfirmPermissions = "Permission overview"
    override val blockConfirmOk = "Got it"
    override val blockDismissContainer = "Container space"
    override val blockDismissLater = "Later"
}
private object EnRoute : RouteTexts {
    override fun travelModeLabel(mode: String): String = when (mode) {
        "DRIVING" -> "Driving"
        "CYCLING" -> "Cycling"
        "WALKING" -> "Walking"
        else -> mode
    }

    override fun routeKindLabel(kind: String): String = when (kind) {
        "POINT_TO_POINT" -> "Point to point"
        "ROAM" -> "Random roam"
        "IMPORTED" -> "Imported track"
        else -> kind
    }

    override fun routeSourceLabel(source: String): String = when (source) {
        "NETWORK" -> "Road network"
        "SYNTHETIC" -> "Built-in curve"
        "IMPORTED" -> "Imported track"
        "ROAM" -> "Random roam"
        else -> source
    }

    override fun routeDegraded(reason: String): String =
        "Road routing unavailable; fell back to the built-in curve ($reason)"

    override fun duration(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> {
            val minutes = seconds / 60
            if (minutes == 1) "1 minute" else "$minutes minutes"
        }
        else -> {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val hourText = if (hours == 1) "1 hour" else "$hours hours"
            if (minutes == 0) hourText else "$hourText $minutes min"
        }
    }

    override fun routeAddressRoam(radiusMeters: Int): String = "Random roam · radius ${radiusMeters} m"
    override fun routeAddressImported(points: Int): String = "Imported track · $points points"
    override fun routeAddressPointToPoint(modeLabel: String): String = "Route cruise · $modeLabel"
    override fun manualNudge(coords: String): String = "Manual nudge · $coords"

    override fun savedNameRoam(radiusMeters: Int): String = "Roam · radius ${radiusMeters} m"
    override fun savedNameImported(points: Int): String = "Imported track · $points points"
    override fun savedNameEndpoints(start: String, end: String): String = "$start → $end"
    override fun endpointSummary(start: String, end: String, waypointCount: Int): String =
        "$start → $end" + if (waypointCount == 0) "" else " · $waypointCount waypoints"

    override val watermarkRoam = "Roam preview · a random loop is generated on start"
    override fun watermarkImported(points: Int): String = "Imported track preview · $points points"
    override val watermarkRoute = "Route preview · planned from the data source on start"
    override fun startLocation(label: String): String = "My location · $label"
    override val addWaypoint = "Add waypoint (optional)"
    override fun waypointCount(count: Int): String = "Waypoints · $count"
    override val chooseEnd = "Choose destination"
    override val routeEnd = "Track endpoint"
    override val cruiseSpeed = "Cruise speed"
    override fun roamCenter(label: String): String = "Roam center · $label"
    override val roamRadius = "Roam radius"
    override val roamSpeed = "Roam speed"
    override val roamHint = "Each session generates one closed random loop and replays it; the loop stays the same for the session."
    override val importedTrack = "Imported track"
    override fun importedTrackLine(name: String, points: Int): String = "$name · $points points"
    override fun importedEndpoints(startCoords: String, endCoords: String): String =
        "Start $startCoords · End $endCoords"
    override val chooseGpx = "Choose GPX file"
    override val gpxHint = "Supports GPX 1.0/1.1 trkpt / rtept; after importing you can cruise right away or save it first."
    override val runningBlockedConfig = "A simulation is already running; stop it on the home screen before starting a cruise"
    override val needRoamCenter = "Choose a roam center to start"
    override val needGpx = "Import a GPX track first"
    override val needEnd = "Choose a destination to start the cruise"
    override fun preview(distance: String, duration: String, points: Int): String =
        "Reference distance $distance · estimate $duration · $points preview points"
    override val favoriteRoute = "Save route"
    override val startRoute = "Start cruise"
    override val loopRoute = "Loop route"
    override val loopRouteSubtitle = "Return to start after the destination"
    override val roundTrip = "Round trip"
    override val roundTripSubtitle = "Come back along the same path"
    override val tapToEdit = "Tap to edit"
    override fun waypointsDialogTitle(count: Int, max: Int): String = "Waypoints · $count/$max"
    override val moveUp = "Move up"
    override val moveDown = "Move down"
    override val editWaypoint = "Edit"
    override val deleteWaypoint = "Delete"
    override val noWaypoints = "No waypoints yet"
    override val limitReached = "Limit reached"
    override val addWaypointButton = "Add waypoint"
    override val done = "Done"

    override val statusError = "Injection error"
    override val statusCompleted = "Route completed"
    override val statusPaused = "Route paused"
    override fun statusRunning(speedKmh: Int, factor: String): String =
        "Cruising · $speedKmh km/h · ${factor}x"
    override val statusIdle = "Route not started"
    override fun watermarkRunning(sourceLabel: String): String = "Route cruise · $sourceLabel"
    override val genericTrack = "Track"
    override fun stopAt(time: String): String = "Stop $time"
    override val exportGpx = "Export GPX"
    override val backHome = "Back to home"
    override fun walked(distance: String): String = "Covered $distance"
    override val arrived = "Destination reached"
    override fun remaining(duration: String): String = "Remaining $duration"
    override val dragToSeek = "Drag to seek"
    override val metricPoints = "Track points"
    override val metricBearing = "Bearing"
    override val metricAccuracy = "Accuracy"
    override val speedFactor = "Speed multiplier"
    override val reverse = "Reverse"
    override val resume = "Resume"
    override val pause = "Pause"
    override val stop = "Stop"
    override val replay = "Replay"
    override fun footer(total: String, index: Int, totalPoints: Int): String =
        "Total $total · point $index/$totalPoints"
    override fun autoStopSuffix(time: String): String = " · auto-stop at $time"

    override val libraryTitle = "Route library"
    override val libraryStart = "Library start"
    override val libraryEnd = "Library destination"
    override val waypointLabel = "Waypoint"
    override val emptyTitle = "No saved routes yet"
    override val emptyMessage = "Tap \"Save route\" on the cruise setup screen, or just start a cruise — recent routes show up here automatically."
    override fun favoritesSection(count: Int): String = "Favorites · $count"
    override fun recentsSection(count: Int): String = "Recent routes · $count"
    override fun libraryHint(maxRecent: Int, maxFavorites: Int): String =
        "Loading only fills the draft; it never starts automatically. Up to $maxRecent recent routes and $maxFavorites favorites; " +
            "favorites are never pushed out of the recent list just because they are unused."
    override val deleteTitle = "Delete route"
    override fun deleteMessage(name: String): String = "Delete \"$name\"? This cannot be undone."
    override val delete = "Delete"
    override val favorite = "Favorite"
    override val unfavorite = "Remove favorite"
    override val rename = "Rename"
    override fun summaryRoam(center: String, radiusMeters: Int, speedKmh: Int): String =
        "Center $center · radius $radiusMeters m · $speedKmh km/h"
    override fun summaryImported(points: Int, speedKmh: Int): String =
        "$points track points · $speedKmh km/h"
    override fun summaryEndpoints(summary: String, speedKmh: Int): String =
        "$summary · $speedKmh km/h"
    override val summaryLoopSuffix = " · Loop"
    override val summaryRoundTripSuffix = " · Round trip"
    override val renameTitle = "Rename route"
    override val renameMessage = "Give this route an easy-to-recognize name."
    override val namePlaceholder = "Route name"
    override val save = "Save"
}
private object EnError : ErrorTexts {
    override val suggestionPrefix = "Suggestion: "

    override fun title(code: com.gpsproxy.dev.core.error.ErrorCode): String = when (code) {
        com.gpsproxy.dev.core.error.ErrorCode.ENG_0102 -> "Device below minimum requirements"
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0102 -> "Not set as mock location app"
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0104 -> "Location permission not granted"
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0101 -> "Failed to write mock location"
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0202 -> "Injection service failed to start"
        com.gpsproxy.dev.core.error.ErrorCode.COORD_0101 -> "Coordinates out of range"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0201 -> "No usable route found"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0302 -> "Route interpolation is empty"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0401 -> "Unable to parse track file"
        com.gpsproxy.dev.core.error.ErrorCode.FILE_0101 -> "Track file read/write failed"
        com.gpsproxy.dev.core.error.ErrorCode.DIAG_0101 -> "Failed to export diagnostics"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0101 -> "Container engine unavailable"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0103 -> "No target app in the container"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0102 -> "Failed to import app into container"
    }

    override fun cause(code: com.gpsproxy.dev.core.error.ErrorCode): String = when (code) {
        com.gpsproxy.dev.core.error.ErrorCode.ENG_0102 ->
            "The system version is lower than Android 8.1, or the device lacks a 64-bit ABI."
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0102 ->
            "The system only allows the selected developer tool to write mock locations."
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0104 ->
            "The foreground location service requires location permission, strictly enforced on Android 14+."
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0101 ->
            "addTestProvider / setTestProviderLocation was rejected by the system."
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0202 ->
            "The system restricted foreground service startup, common when starting in the background or under battery optimization."
        com.gpsproxy.dev.core.error.ErrorCode.COORD_0101 ->
            "Latitude must be -90..90 and longitude -180..180."
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0201 ->
            "Start and end are too close, or the interpolated path is empty."
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0302 ->
            "Route generation failed to produce valid samples."
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0401 ->
            "The GPX is corrupted, lacks trkpt/rtept, or has fewer than 2 valid points."
        com.gpsproxy.dev.core.error.ErrorCode.FILE_0101 ->
            "The file URI grant expired, storage is full, or the provider denied access."
        com.gpsproxy.dev.core.error.ErrorCode.DIAG_0101 ->
            "The cache directory is not writable or no share target is available."
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0101 ->
            "The virtualization engine did not start with the app, or the container process is unreachable; Android 15+ is experimental."
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0103 ->
            "The root-free channel only works inside the container, and no target app has been imported yet."
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0102 ->
            "APK parsing failed, the ABI is incompatible, or container storage is full."
    }

    override fun action(code: com.gpsproxy.dev.core.error.ErrorCode): String = when (code) {
        com.gpsproxy.dev.core.error.ErrorCode.ENG_0102 ->
            "Switch to an arm64 device running Android 8.1+ and try again. This version has no degraded mode."
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0102 ->
            "Open Developer options → Select mock location app → Choose GpsProxy."
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0104 ->
            "Grant \"Precise location\" in the permission overview."
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0101 ->
            "Make sure this app is selected as the mock location app, and disable app freezing/pure mode."
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0202 ->
            "Keep the app in the foreground and start again, and allow ignoring battery optimizations."
        com.gpsproxy.dev.core.error.ErrorCode.COORD_0101 ->
            "Correct the input, or pick a point on the map again."
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0201 ->
            "Choose another destination; keep start and end more than 10 m apart."
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0302 ->
            "Pick the destination again before starting the cruise."
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0401 ->
            "Verify the file is GPX 1.0/1.1 with track points and try again."
        com.gpsproxy.dev.core.error.ErrorCode.FILE_0101 ->
            "Choose the file again, or use a writable local directory."
        com.gpsproxy.dev.core.error.ErrorCode.DIAG_0101 ->
            "Clear the cache and retry, or export manually with a file manager."
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0101 ->
            "Restart the app and re-check on the container init page; if it still fails, use the official mock channel."
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0103 ->
            "Open the container apps page and import a target app from installed apps or an APK."
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0102 ->
            "Try another APK, or uninstall unused container apps to free space."
    }
}

private object EnPlace : PlaceTexts {
    override val searchTitle = "Search places"
    override val searchPlaceholder = "Search places, addresses, transit stops"
    override val searchAction = "Search"
    override fun searchTabLabel(tab: String): String = when (tab) {
        "KEYWORD" -> "Keyword"
        "NEARBY" -> "Nearby"
        "HISTORY" -> "History"
        else -> tab
    }

    override fun poiCategoryLabel(category: String): String = when (category) {
        "ALL" -> "All"
        "SCENIC" -> "Scenic"
        "FOOD" -> "Food"
        "TRANSIT" -> "Transit"
        "MALL" -> "Mall"
        "HOTEL" -> "Hotel"
        "HOSPITAL" -> "Hospital"
        "OTHER" -> "Other"
        else -> category
    }

    override fun poiSourceLabel(source: String): String = when (source) {
        "LOCAL" -> "Built-in sample data"
        "AMAP" -> "AMap"
        else -> source
    }

    override fun dataSource(label: String): String = "Data source: $label"
    override val builtinHint = "(Configure an AMap Web Service key in local.properties to enable real POIs)"
    override val searchFailed = "Search failed"
    override val searchError = "Network error; try again later or use the built-in sample data"
    override val fallbackWarning = "Network error; fell back to the built-in sample data"
    override val unnamedPlace = "Unnamed place"
    override fun nearAddress(city: String, name: String): String = "$city · near $name"
    override val emptyHistoryTitle = "No search history yet"
    override val emptyHistoryMessage = "Searched keywords are kept for the 20 most recent entries."
    override val recentSearches = "Recent searches"
    override val clear = "Clear"
    override val noResultsTitle = "No matching places"
    override val noResultsMessage = "Try another keyword, or long-press the map to pick a point."
    override val goPick = "Pick on map"
    override val loading = "Loading…"
    override val loadMore = "Load more"

    override val favoritesTitle = "Favorites & history"
    override val addNew = "Add"
    override val favoritesTab = "Favorite places"
    override val historyTab = "History"
    override val emptyFavoritesTitle = "No favorite places yet"
    override val emptyFavoritesMessage = "Pick a point on the map and tap \"Favorite\"; frequent places show up here."
    override val mapPick = "Pick on map"
    override val noHistoryTitle = "No history yet"
    override val noHistoryMessage = "Every time you set a mock location or start a cruise, one recent entry is kept."
    override fun recentCount(count: Int): String = "Last $count entries"
    override fun deletedUndo(name: String): String = "Deleted \"$name\""
    override val undo = "Undo"
    override val renameFavorite = "Rename favorite"
    override val renameFavoriteHint = "Up to 20 characters"

    override val pickWatermark = "Long-press to pick · nudge step 1 m"
    override val reverseResolving = "Resolving address…"
    override fun mockPickAddress(coords: String): String = "Mock pick · $coords"
    override val chipRouteEnd = "Set route destination"
    override val chipWaypoint = "Set waypoint"
    override val reverseTag = "Reverse geocode"
    override fun blurNotice(radiusMeters: Int): String =
        "Location blur is on: the injected location shifts within ±$radiusMeters m, so apps cannot recover this pick"
    override val pasteCoords = "Paste coordinates"
    override val copy = "Copy"
    override val latitude = "Latitude"
    override val longitude = "Longitude"
    override val invalidRangeMessage = "Latitude must be -90~90 and longitude -180~180. Fix the values to continue."
    override val setAsWaypoint = "Set as waypoint"
    override val setAsEnd = "Set as destination"
    override val setAsMock = "Set as mock location"
    override val pickHintPrefix = "Long-press anywhere to re-pick · nudge step 1 m · "
    override val waypointOrderHint = "Waypoints are connected in the order selected"
    override fun coordSystemHint(label: String): String = "Coordinate system $label"
    override val unparsableCoords = "Unrecognized coordinates; supports 30.25,120.15, DMS or geo: links"
    override val clipboardEmpty = "Clipboard is empty; copy some coordinates first"
    override val pickFirst = "Pick a point on the map first"
    override val mockStarted = "Simulation started"
}

private object EnContainer : ContainerTexts {
    override val targetAppsTitle = "Target apps"
    override val refresh = "Refresh"
    override val commonAppsTitle = "Frequent target apps"
    override val commonAppsBanner =
        "These entries only bookmark frequent apps for one-tap launching. To mock the location of an app, " +
            "import it into the container (container proxy channel: root-free, no isMock flag), " +
            "or use the system Mock channel (applies to all apps, but carries the isMock flag)."
    override val filterPlaceholder = "Filter by app name or package"
    override val noLaunchableAppsTitle = "No launchable apps found"
    override val noLaunchableAppsMessage = "Check that the system does not restrict app visibility, then tap refresh in the top-right."
    override val sectionCommon = "Frequent apps"
    override fun sectionAllApps(count: Int): String = "All apps · $count"
    override val systemBadge = "System"
    override fun openApp(label: String): String = "Open $label"
    override val removeCommon = "Remove from frequent"
    override val addCommon = "Add to frequent"
    override val removedCommon = "Removed from frequent target apps"
    override val addedCommon = "Added to frequent target apps"
    override val cannotLaunchApp = "Cannot launch this app"

    override val containerAppsTitle = "Container apps"
    override val engineUnavailableTitle = "Container engine unavailable"
    override val engineUnavailableText =
        "The root-free channel depends on the container engine; run the engine check first. " +
            "When unavailable, switch to the system Mock channel (global scope, but requires system authorization and marks coordinates with isMock)."
    override val goEngineCheck = "Run engine check"
    override val readsMockTitle = "Container apps read mock coordinates"
    override val readsMockText =
        "Imported apps run in an isolated sandbox; once simulation starts, their location APIs return mock coordinates " +
            "without passing through system test providers, so coordinates carry no isMock flag. Uninstalling inside the container does not affect the original app on the host."
    override fun sandboxUsage(megabytes: String): String = "Sandbox usage $megabytes MB"
    override val processing = "Processing…"
    override val importInstalled = "Import installed app"
    override val importApk = "Import APK file"
    override val restoreAll = "Restore all container locations"
    override val loadAppsFailedTitle = "Failed to read container apps"
    override val loadAppsFailedMessage =
        "The container service is temporarily unreachable; it may be starting or frozen by the system. Tap refresh in the top-right later; imported apps are not lost."
    override val emptyContainerTitle = "No apps in the container yet"
    override val emptyContainerMessage = "Tap a button above to import from installed apps or an APK file."
    override fun containerAppsSection(count: Int): String = "Container apps · $count"
    override val cellSynced = "Cell towers synced"
    override fun locationModeLabel(mode: String): String = when (mode) {
        "CLOSE" -> "Off"
        "GLOBAL" -> "Follow"
        "OWN" -> "Own"
        else -> mode
    }

    override val ownBadge = "Own"
    override val useCurrentPoint = "Use current pick"
    override val resumeFollow = "Resume following"
    override fun launchInContainer(label: String): String = "Launch $label in container"
    override fun uninstallFromContainer(label: String): String = "Uninstall $label from container"
    override fun importApp(label: String): String = "Import $label"
    override val unreadableFile = "Cannot read the selected file"
    override val readFileFailed = "Failed to read the file"
    override val cannotLaunchInContainer = "Cannot launch this app inside the container"
    override val uninstalled = "Uninstalled from the container"
    override val uninstallFailed = "Uninstall failed"
    override fun modeChanged(packageLabel: String, modeLabel: String): String =
        "$packageLabel set to $modeLabel"
    override val switchFailed = "Switch failed: container service unavailable"
    override val pickFirst = "Pick a point on the home screen first"
    override fun ownSet(packageName: String): String = "$packageName now uses its own coordinates"
    override val ownSetFailed = "Failed to set own coordinates"
    override fun ownCleared(packageName: String): String = "$packageName no longer uses own coordinates"
    override val ownClearFailed = "Failed to clear own coordinates"
    override val restored = "Container location state restored"
    override fun imported(packageName: String): String = "Imported into container: $packageName"
    override val unknownReason = "Unknown reason"
    override val engineUnavailable = "Container engine unavailable"
    override val appPackageNotFound = "Installation package for this app not found"
    override val apkFileMissing = "APK file does not exist"
    override val importSucceeded = "Import succeeded"
    override val importFailed = "Import failed"
    override val importFailedConnection = "Import failed: container service connection error"

    override val initTitle = "Container engine"
    override val initInitializing = "Initializing the virtual container"
    override val initReady = "Container engine ready"
    override val initUnavailable = "Container engine unavailable"
    override val initSubtitle = "Target apps run in an isolated sandbox; their location APIs read the mock coordinates"
    override val preparing = "Preparing…"
    override fun checksDone(done: Int, total: Int): String = "$done / $total checks completed"
    override val experimentalTitle = "Experimental support on Android 15+"
    override val experimentalText =
        "Container support on this Android version is experimental and has been verified on emulators; behavior on physical devices may differ. " +
            "When the container is unavailable, the system location is never rewritten automatically; switch to the system Mock channel manually."
    override val manageApps = "Manage container apps"
    override val recheck = "Run check again"
}

private object EnOnboarding : OnboardingTexts {
    override val appTitle = "Location simulator"
    override val tagline = "Location verification tool for developers and authorized testing\nContainer proxy · Root-free · Map picking · Route simulation"
    override val boundarySection = "Usage boundaries"
    override val boundaryTestOnly = "Use only with your own apps or test scenarios you are authorized to run."
    override val boundaryChannels =
        "The container proxy channel is root-free and never writes to the system mock; coordinates read inside the container carry no isMock flag. " +
            "The system Mock channel still requires Developer options authorization and marks coordinates with isMock=true."
    override val boundaryScope = "Scope is limited to apps imported into the container; the original apps on the host are never modified."
    override val boundaryLiability = "You are responsible for the consequences of using this app for attendance fraud, location deception or similar scenarios."
    override val consentLabel = "I have read and understood the usage boundaries and disclaimer above, and I promise to use this app legally and compliantly."
    override val agreeContinue = "Agree and continue"
    override val declineExit = "Decline and exit"
    override fun versionLine(version: String): String =
        "v$version · Supports arm64 devices on Android 8.1 and above"

    override val permissionsTitle = "Permission overview"
    override fun permissionsReady(done: Int, total: Int): String = "$done / $total ready"
    override val hintNeedMockApp = "Without the \"mock location app\" authorization, no coordinates can be written to the system."
    override val hintNeedLocation = "Without location permission, Android 14+ cannot start the foreground location service."
    override val hintNeedOverlay = "Without overlay permission, the floating ball cannot appear over target apps."
    override val hintAllReady = "Essential permissions are ready; you can start a simulation."
    override val permLocationTitle = "Location (precise)"
    override val permLocationSubtitle = "Foreground location service and map browsing; real tracks are never read or uploaded"
    override val permNotificationTitle = "Notifications"
    override val permNotificationSubtitle = "Keeps a persistent notification for the foreground service, reducing the chance of the system killing it"
    override val permOverlayTitle = "Overlay"
    override val permOverlaySubtitle = "Shows the floating ball and quick panel over other apps"
    override val permMockAppTitle = "Mock location app"
    override val permMockAppSubtitle = "Developer options → Select mock location app → GpsProxy"
    override val permBatteryTitle = "Ignore battery optimization"
    override val permBatterySubtitle = "Reduces the chance of the system killing long cruises or locked-screen sessions"
    override val privacyBanner =
        "This app never requests call logs, SMS, contacts or other permissions unrelated to location. " +
            "The container proxy channel is root-free and never writes to the system mock; coordinates read inside the container carry no isMock flag. " +
            "The system Mock channel is marked by the system as isMock=true."
    override val refreshStatus = "Refresh authorization status"
    override val mockAppDialogTitle = "Enable as mock location app"
    override val mockAppSteps = listOf(
        "1. Open the system \"Developer options\"",
        "2. Find \"Select mock location app\"",
        "3. Select GpsProxy in the list",
        "4. Return to this page and refresh the status",
    )
    override val mockAppFootnote = "Entry names differ slightly on some ROMs; if the option is missing, the system restricts mock capabilities."
    override val goDeveloperOptions = "Open Developer options"
    override val granted = "Granted"
    override val goGrant = "Grant"
    override val locationGranted = "Location permission granted"
    override val locationDenied = "Location permission not granted"

    override val engineTitle = "Injection engine"
    override val engineInitializing = "Initializing the injection engine"
    override val engineReady = "Injection engine ready"
    override val engineSubtitle = "Virtual container proxy location: root-free, no repackaging, no modification of target apps"
    override val containerNotReadyTitle = "Container runtime not ready"
    override val containerNotReadyText =
        "The root-free channel depends on the BlackBox virtual container. Retry on the container check page; " +
            "if it still fails, only the system Mock channel is available (requires Developer options authorization and marks coordinates with isMock)."
    override val checkContainer = "Check container runtime"
    override val enterHome = "Continue to home"
    override val viewUnsupportedReason = "See why it is unsupported"
    override val recheck = "Run check again"

    override val deviceCheckTitle = "Device check"
    override val unsupportedTitle = "This device is not supported"
    override val unsupportedSubtitle = "The injection engine cannot run safely in this environment, so use is blocked."
    override val unsupportedFallbackIssue = "Device capability check failed"
    override fun errorCodeLine(code: String): String = "Error code: $code"
    override val unsupportedMessage = "The device does not meet the minimum requirements. This is a hard block and cannot be skipped."
    override fun currentEnv(apiLevel: Int, abiLabel: String): String =
        "Current environment: Android $apiLevel · $abiLabel"
    override val viewSupportedDevices = "View supported devices"
    override val supportScopeTitle = "Supported scope"
    override val supportScopeText =
        "arm64 devices on Android 8.1 – 15; a working system location service is required. " +
            "HarmonyOS NEXT does not provide the Android runtime, so this app cannot be installed."
    override val gotIt = "Got it"

    override val issueNo64Bit = "The system only provides 32-bit ABIs; this tool cannot run"
    override val issueHarmony = "HarmonyOS NEXT does not provide the Android runtime"
    override val unknownAbi = "Unknown"

    override val splashTagline = "Official Mock Location · Developer location verification"
    override val mapMarkerLabel = "Mock location"
}

private object EnSelfCheck : SelfCheckTexts {
    override val engineVersionTitle = "Check system version and ABI"
    override fun engineVersionPending(apiLevel: Int): String = "Android $apiLevel"
    override fun engineSupported(apiLevel: Int, abiLabel: String): String = "Android $apiLevel · $abiLabel"
    override val engineUnsupported = "Device does not meet the minimum requirements"
    override val engineContainerTitle = "Check container virtualization runtime"
    override val engineContainerPending = "BlackBox engine"
    override val engineContainerReady = "Container runtime ready · root-free"
    override val engineContainerUnavailable = "Container runtime unavailable; root-free channel unavailable"
    override val engineProxyTitle = "Check container location proxy"
    override val engineProxyPending = "ILocationManager proxy"
    override val engineProxyReady = "Location proxy readable and writable"
    override val engineProxyUnavailable = "Container location proxy not ready"
    override val engineAppsTitle = "Check container apps"
    override val engineAppsPending = "Imported target apps"
    override fun appsImported(count: Int): String = "$count apps imported"
    override val noAppsImported = "No apps imported yet; import them on the container apps page first"
    override val engineMockTitle = "Check system Mock (fallback)"
    override val engineMockPending = "Developer options mock location authorization"
    override val engineMockReady = "Fallback channel available (coordinates carry the isMock flag)"
    override val engineMockUnavailable = "Not authorized; only the container channel is available"

    override val containerRuntimeTitle = "Virtualization runtime"
    override val containerRuntimePending = "BlackBox engine loading"
    override val containerRuntimeReady = "Engine started with the app"
    override val containerRuntimeFailed = "Engine failed to load; only the official Mock channel is available"
    override val abiTitle = "ABI compatibility check"
    override val abiPending = "Container process architecture"
    override fun abiDetail(abiLabel: String): String = "64-bit ABI · $abiLabel"
    override val abiMissing = "64-bit ABI missing"
    override val hookTitle = "Hook framework"
    override val hookPending = "System service proxies"
    override val hookReady = "AMS / PMS / Location proxies ready"
    override val hookFailed = "System service proxies unavailable"
    override val binderTitle = "Binder channel"
    override val binderPending = "Container service connection"
    override val binderReady = "Container service process connected"
    override val binderFailed = "Cannot connect to the container service process"
    override val locationProxyTitle = "Location proxy"
    override val locationProxyPending = "ILocationManager / cell proxy"
    override val locationProxyReady = "Coordinate / cell callback channel available"
    override val locationProxyFailed = "Location proxy not ready; container apps will read the real location"

    override fun exception(error: String): String = "Check failed: $error"
}
private object EnChannel : ChannelTexts {
    override fun channelName(id: String): String = when (id) {
        "CONTAINER" -> "Container location proxy"
        "GPS" -> "LocationManager · GPS"
        "NETWORK" -> "LocationManager · Network"
        "FUSED" -> "Fused Location"
        "SDK" -> "AMap / Tencent location SDK"
        "SENSOR" -> "Sensor / pedometer"
        "CELL" -> "Cell tower fingerprint"
        "WIFI" -> "Wi-Fi location"
        else -> id
    }

    override fun channelDescription(id: String): String = when (id) {
        "CONTAINER" -> "Hooks ILocationManager inside the sandbox; injected coordinates never touch the system mock providers and carry no isMock flag"
        "GPS" -> "Official mock channel: writes to the GPS test provider (requires Developer options authorization)"
        "NETWORK" -> "Official mock channel: writes to the network location test provider"
        "FUSED" -> "Without GMS in the container, the LocationManager proxy takes over; the official channel relies on system forwarding only"
        "SDK" -> "SDKs inside the container use system location APIs and are therefore covered; host-side SDKs are unaffected"
        "SENSOR" -> "This version does not inject sensor or step data"
        "CELL" -> "Derives local cell towers from the coordinates and sends them to TelephonyManager inside the container in sync with the mock location"
        "WIFI" -> "Wi-Fi data inside the container is resolved by the sandbox proxy; host-side scan results are out of scope"
        else -> ""
    }

    override fun note(kind: String, count: Int): String = when (kind) {
        "CONTAINER_READY" -> "Proxying $count container app(s) · root-free"
        "CONTAINER_EMPTY" -> "No apps to proxy inside the container; import target apps first"
        "CONTAINER_UNAVAILABLE" -> "Container runtime unavailable; cannot inject"
        "CONTAINER_IDLE" -> "Primary root-free channel, waiting for the container"
        "MOUNTED_OFFICIAL" -> "Mounted · official mock"
        "NOT_MOUNTED" -> "Not mounted"
        "MOUNTED_FUSED" -> "Test provider mounted"
        "FUSED_NOT_EXPOSED" -> "System does not expose a fused test provider"
        "SYNCED" -> "Delivered · syncs with coordinates"
        "RUNTIME_READY" -> "Runtime ready · root-free"
        "MOCK_STANDBY" -> "Standby channel, waiting to start"
        "NOT_MOCK_APP" -> "Not set as mock location app"
        "FOLLOWS_CONTAINER" -> "Delivered via the container channel"
        "STOPPED_CONTAINER" -> "Stopped; container apps use the real location again"
        "STOPPED" -> "Stopped"
        "FUSED_FORWARD_ONLY" -> "Relies on system forwarding only"
        "NOT_FORCED" -> "Cannot be forced directly"
        "CONTAINER_ONLY" -> "Only applies to sandboxed apps in the container"
        "NOT_PROVIDED" -> "Not provided"
        "CONTAINER_ACTIVE" -> "Active with the container channel"
        "HOST_OUT_OF_SCOPE" -> "Host side is out of scope"
        else -> ""
    }

    override fun hitCount(count: Long): String = if (count == 1L) "1 hit" else "$count hits"
    override val statusMiss = "No hit"
    override val statusUnavailable = "Unavailable"
    override val statusDisabled = "Not enabled"

    override val pageTitle = "Runtime diagnostics"
    override val export = "Export diagnostics"
    override val exportContentDescription = "Export diagnostics"
    override val infoBanner = "Each injection channel is probed individually: channels that hit return the mock coordinates; channels that miss are shown as-is, with no embellishment."
    override val channelsSection = "Injection channels"
    override val mockFlagSection = "Mock flag"
    override val mockFlagContainerTitle = "isMock=false (container proxy)"
    override val mockFlagContainerText =
        "Coordinates are resolved by the ILocationManager proxy inside the container and never pass through a system test provider, " +
            "so Location.isFromMockProvider() is false. Scope: imported apps inside the container only."
    override val mockFlagOfficialTitle = "isMock=true (system mock)"
    override val mockFlagOfficialText =
        "Coordinates are written by Android's official mock location provider, so the system marks them as mock. " +
            "This channel affects all apps on the host, and the flag cannot be hidden."
    override val blurSection = "Location blur"
    override fun blurOn(radiusMeters: Int): String = "Active · ±$radiusMeters m"
    override val blurOff = "Inactive"
    override fun blurActiveText(injectedCoords: String, selectedCoords: String, radiusMeters: Int): String =
        "Apps read the shifted coordinates ($injectedCoords), which differ from the picked point ($selectedCoords) by no more than " +
            "±$radiusMeters m (plus ≤1.25 m of natural drift). The offset is fixed for the session and does not drift per update."
    override val blurInactiveText =
        "When enabled, the real picked point is shifted once within the configured radius, so apps cannot reconstruct the user's selection."
    override val errorsSection = "Recent errors"
    override val errorNone = "No errors were recorded in this session. A missed channel is not an anomaly; see the per-channel status above."
    override fun logsSection(count: Int): String = "Live log · last $count entries"
    override val clearLogs = "Clear log"
    override val exportHint = "The diagnostics package contains device info, channel status and redacted logs; it contains no target app data."
    override val exportDialogMessage =
        "A ZIP (device info, channel status, redacted logs) will be generated and shared via the system share sheet.\n\n" +
            "Logs contain no target app content, accounts or contacts, but do include the current mock coordinates. Continue?"
    override val exportConfirm = "Export"
    override val noLogs = "No logs yet"
}
private object EnMessage : MessageTexts {
    override val simulationStopped = "Simulation stopped; real location restored"
    override val injectionPaused = "Injection paused; coordinates held"
    override val injectionResumed = "Injection resumed"
    override val noMapApp = "No app available to open the map"
    override val coordsCopied = "Coordinates copied"
    override val addedToFavorites = "Added to favorites"
    override fun favoriteName(coords: String): String = "Mock point · $coords"
    override fun startSuccess(channel: String): String = when (channel) {
        "CONTAINER" -> "Simulation started; target apps inside the container will read these coordinates (root-free)"
        "OFFICIAL" -> "Simulation started; apps on the system will read these coordinates"
        else -> "Simulation started"
    }
    override val logsCleared = "Logs cleared"
    override fun diagnosticsExported(fileName: String): String = "Diagnostics package generated: $fileName"

    override val autoStopped = "Session time limit reached; stopped automatically"
    override val routeStartPointSet = "Start set to the current mock location"
    override val routeIncomplete = "Complete the route first"
    override val routeFavorited = "Current route saved"
    override val routeSaveFailed = "Failed to save the route; try again later"
    override fun trackImported(points: Int): String = "Imported $points track points"
    override val routeNeedRoamCenter = "Set a roam center first"
    override val routeNeedGpx = "Import a GPX track first"
    override val routeNeedEndpoints = "Set the start and destination first"
    override val routeAlreadyRunning = "A simulation is already running; stop it on the home screen first"
    override val routeStarted = "Cruise started"
    override fun routeLoaded(name: String): String = "Loaded \"$name\" into the draft"
    override val routeUnfavorited = "Removed from favorites"
    override val routeFavoriteAdded = "Added to favorites"
    override val routeUpdateFailed = "Failed to update the route"
    override val routeRenamed = "Route renamed"
    override val routeRenameFailed = "Rename failed"
    override fun routeDeleted(name: String): String = "Deleted \"$name\""
    override val routeDeleteFailed = "Delete failed"
    override fun speedFactorChanged(factor: String): String = "Cruise speed ${factor}x"
    override val routeDirectionSwitched = "Cruise direction switched"
    override val routeReplayed = "Replayed from the start"
    override val routeCannotReplay = "Cannot replay in the current state"
    override val noTrackToExport = "No track available to export"
    override val gpxExported = "GPX track exported"
    override val defaultGpxName = "GpsProxy track"
}
private object EnNotification : NotificationTexts {
    override val channelName = "Location injection service"
    override val channelDescription = "Keeps the mock location session running"
    override val titlePaused = "Simulation paused"
    override val titleCompleted = "Route completed"
    override val titleError = "Injection error"
    override val titleActive = "Simulation active"
    override val titleIdle = "Location simulation service"
    override val noPoint = "No coordinates selected"
    override fun blurSuffix(radiusMeters: Int): String = " · blur ±${radiusMeters}m"
    override fun autoStopSuffix(time: String): String = " · auto-stop $time"
    override fun compactBody(point: String, accuracyMeters: Int): String = "$point · ±$accuracyMeters m"
    override fun detailedBody(
        point: String,
        accuracyMeters: Int,
        coordSystem: String,
        blurSuffix: String,
        autoStopSuffix: String,
    ): String = "$point · ±$accuracyMeters m · $coordSystem$blurSuffix$autoStopSuffix"
    override val subTextContainer = "Container proxy · isMock=false"
    override val subTextOfficial = "System mock · isMock=true"
    override val resume = "Resume"
    override val pause = "Pause"
    override val stop = "Stop"

    override val bootChannelName = "Session recovery reminder"
    override val bootChannelDescription = "Reminds you of an unfinished simulation after reboot"
    override val bootTitle = "Your last simulation did not finish"
    override fun bootText(point: String): String = "$point · Tap to open GpsProxy and continue"
    override val lastPoint = "Last picked point"
}

private object EnLog : LogTexts {
    override fun routeHistoryFailed(error: String): String = "route history record failed: $error"
    override fun targetSet(coords: String, address: String): String = "target set: $coords · $address"
    override fun singleStarted(channel: String, blurSuffix: String): String =
        "single-point simulation started · channel=$channel$blurSuffix"
    override fun blurSuffix(meters: Int): String = " (blur ±${meters}m)"
    override fun routeStarted(
        kind: String,
        mode: String,
        speedKmh: Int,
        distance: String,
        points: Int,
        source: String,
        blurSuffix: String,
    ): String = "route started: $kind $mode ${speedKmh}km/h, ${distance}m, $points points · $source$blurSuffix"
    override val paused = "injection paused; position held"
    override val resumed = "injection resumed"
    override fun speedFactorSet(factor: String): String = "route speed factor set to ${factor}x"
    override val seekIgnored = "seek ignored: route completed, restart first"
    override fun seeked(percent: Int): String = "route seeked to $percent%"
    override val reverseIgnored = "reverse ignored: route is not running or paused"
    override fun direction(forward: Boolean): String =
        if (forward) "route direction=forward" else "route direction=backward"
    override val restarted = "route restarted from origin"
    override val walkIgnored = "walk ignored: single-point session is not running"
    override val stopped = "simulation stopped; real location restored"
    override val nudgeIgnored = "nudge ignored while route is running"
    override fun blurEnabled(meters: Int): String = "location blur enabled ±${meters}m"
    override val blurDisabled = "location blur disabled"
    override fun pushRetry(attempt: Int, max: Int): String = "push failed ($attempt/$max), retrying"
    override fun injectionFailed(title: String): String = "injection failed: $title"
    override fun injected(
        coords: String,
        speed: String,
        bearing: String,
        accuracy: Int,
        satellites: Int,
        channel: String,
    ): String = "inject $coords speed=$speed bearing=$bearing accuracy=$accuracy sat=$satellites channel=$channel"
    override val walkLatitudeLimit = "walk stopped: latitude limit reached"
    override val routeCompleted = "route completed at endpoint"
    override fun failure(code: String, cause: String): String = "$code · $cause"
    override fun autoStopped(minutes: Int): String = "auto-stop deadline reached after ${minutes}min"
    override val pumpCompleted = "simulation pump completed"
    override fun pumpStopped(code: String): String = "simulation pump stopped: $code"
    override val containerUnavailable = "container channel unavailable"
    override val containerNoApps = "container channel has no target apps"
    override fun providerIssues(issues: String): String = "provider issues: $issues"
    override fun officialPushFailed(issues: String): String = "official mock push failed: $issues"
    override fun containerMirrorRetry(millis: Long): String = "container mirror push failed; retry in ${millis}ms"
    override fun cellFingerprint(lac: Int, cid: Int, dbm: Int): String =
        "cell fingerprint lac=$lac cid=$cid dbm=$dbm"
    override val locationManagerUnavailable = "LocationManager unavailable"
    override fun providerDenied(provider: String): String = "$provider: not authorized to write mock location"
    override val noTestProvider = "No test provider available"
}
