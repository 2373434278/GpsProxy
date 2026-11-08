package com.gpsproxy.dev.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsproxy.dev.core.geo.RoutePath
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.domain.simulation.model.TravelMode
import com.gpsproxy.dev.domain.place.DefaultLocation
import com.gpsproxy.dev.ui.feature.route.RouteViewModel
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.components.FinePrint
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpIconButton
import com.gpsproxy.dev.ui.components.GpSegmented
import com.gpsproxy.dev.ui.components.GpSwitch
import com.gpsproxy.dev.ui.components.StatusChip
import com.gpsproxy.dev.ui.components.StatusTone
import com.gpsproxy.dev.ui.components.SurfaceCard
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.map.SchematicMap
import com.gpsproxy.dev.ui.map.rememberMapCamera
import com.gpsproxy.dev.ui.session.RouteDraftStore
import com.gpsproxy.dev.ui.session.RouteWaypoint
import com.gpsproxy.dev.ui.theme.Gp
import kotlin.math.roundToInt

@Composable
fun RouteConfigScreen(
    vm: RouteViewModel,
    onBack: () -> Unit,
    onPickEnd: () -> Unit,
    onPickWaypoint: (Int) -> Unit,
    onOpenRoutes: () -> Unit,
    onStarted: () -> Unit,
) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val sessionActive by vm.sessionActive.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    LaunchedEffect(Unit) { vm.initializeStart() }

    val start = draft.start ?: DefaultLocation.POINT
    val end = draft.end
    val fullPreviewPoints = remember(draft) { draft.previewPoints() }
    val mapPreviewPoints = remember(fullPreviewPoints) {
        com.gpsproxy.dev.core.geo.GeoUtils.downsample(fullPreviewPoints, 2_000)
    }
    val distance = remember(fullPreviewPoints) {
        if (fullPreviewPoints.size > 1) RoutePath(fullPreviewPoints).totalDistance else 0.0
    }
    val durationSeconds = if (draft.speedKmh > 0) {
        (distance / (draft.speedKmh / 3.6)).roundToInt()
    } else {
        0
    }
    val camera = rememberMapCamera(start, 14.5f)
    LaunchedEffect(draft.start) {
        draft.start?.let { camera.center = it }
    }
    var showWaypoints by remember { mutableStateOf(false) }
    val gpxPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { vm.importGpx(it.toString()) }
    }
    val openGpx: () -> Unit = {
        gpxPicker.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*"))
    }

    Box(Modifier.fillMaxSize()) {
        SchematicMap(
            modifier = Modifier.fillMaxSize(),
            camera = camera,
            marker = start,
            routePoints = mapPreviewPoints,
            dashedRoute = draft.kind != RouteKind.IMPORTED,
            routeStart = start,
            routeEnd = when (draft.kind) {
                RouteKind.ROAM -> null
                RouteKind.IMPORTED -> draft.trackPoints.lastOrNull()
                RouteKind.POINT_TO_POINT -> end
            },
            waypoints = draft.waypoints.map { it.point },
            accuracyMeters = null,
            zoomControls = false,
            watermark = when (draft.kind) {
                RouteKind.ROAM -> strings.route.watermarkRoam
                RouteKind.IMPORTED -> strings.route.watermarkImported(draft.trackPoints.size)
                RouteKind.POINT_TO_POINT -> strings.route.watermarkRoute
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GpIconButton(
                icon = GpIcons.Back,
                contentDescription = strings.common.back,
                onClick = onBack,
                background = Color.White,
                tint = Gp.Ink,
            )
            Spacer(Modifier.weight(1f))
            StatusChip(text = strings.route.routeKindLabel(draft.kind.name), tone = StatusTone.Idle)
            GpIconButton(
                icon = GpIcons.Route,
                contentDescription = strings.route.libraryTitle,
                onClick = onOpenRoutes,
                background = Color.White,
                tint = Gp.Ink,
            )
        }

        SurfaceCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 14.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = 20.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        ) {
            Box(
                Modifier
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFFE0E5EC))
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))

            GpSegmented(
                options = RouteKind.entries.map { strings.route.routeKindLabel(it.name) },
                selectedIndex = RouteKind.entries.indexOf(draft.kind),
                onSelect = { vm.setKind(RouteKind.entries[it]) },
            )
            Spacer(Modifier.height(14.dp))

            when (draft.kind) {
                RouteKind.POINT_TO_POINT -> {
                    RouteLine(
                        dotColor = Gp.Green,
                        value = strings.route.startLocation(
                            draft.startAddress.ifBlank { DefaultLocation.ADDRESS },
                        ),
                        onClick = vm::setStartToCurrent,
                    )
                    Spacer(Modifier.height(8.dp))
                    RouteLine(
                        dotColor = Color(0xFF98A2B3),
                        value = if (draft.waypoints.isEmpty()) {
                            strings.route.addWaypoint
                        } else {
                            strings.route.waypointCount(draft.waypoints.size)
                        },
                        placeholder = draft.waypoints.isEmpty(),
                        onClick = {
                            if (draft.waypoints.isEmpty()) {
                                vm.prepareAddWaypoint()
                                onPickWaypoint(RouteDraftStore.ADD_WAYPOINT)
                            } else {
                                showWaypoints = true
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    RouteLine(
                        dotColor = Gp.Red,
                        value = if (end == null) {
                            strings.route.chooseEnd
                        } else {
                            draft.endAddress.ifBlank { strings.route.routeEnd }
                        },
                        placeholder = end == null,
                        onClick = {
                            vm.preparePickEnd()
                            onPickEnd()
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    GpSegmented(
                        options = TravelMode.entries.map { strings.route.travelModeLabel(it.name) },
                        selectedIndex = TravelMode.entries.indexOf(draft.mode),
                        onSelect = { vm.setMode(TravelMode.entries[it]) },
                    )
                    Spacer(Modifier.height(12.dp))
                    SpeedSlider(
                        title = strings.route.cruiseSpeed,
                        speedKmh = draft.speedKmh,
                        range = 5f..80f,
                        onSpeed = vm::setSpeed,
                    )
                    Spacer(Modifier.height(8.dp))
                    LoopToggles(
                        loop = draft.loop,
                        roundTrip = draft.roundTrip,
                        onLoop = vm::setLoop,
                        onRoundTrip = vm::setRoundTrip,
                    )
                }

                RouteKind.ROAM -> {
                    RouteLine(
                        dotColor = Gp.Green,
                        value = strings.route.roamCenter(
                            draft.startAddress.ifBlank { DefaultLocation.ADDRESS },
                        ),
                        onClick = vm::setStartToCurrent,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings.route.roamRadius, color = Gp.Ink2, fontSize = 12.sp)
                        Text(
                            "${draft.roamRadiusMeters} m",
                            color = Gp.Ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Slider(
                        value = draft.roamRadiusMeters.toFloat(),
                        onValueChange = { vm.setRoamRadius((it / 50f).roundToInt() * 50) },
                        valueRange = 100f..5000f,
                        colors = SliderDefaults.colors(
                            thumbColor = Gp.Blue,
                            activeTrackColor = Gp.Blue,
                            inactiveTrackColor = Color(0xFFE5EAF2),
                        ),
                    )
                    SpeedSlider(
                        title = strings.route.roamSpeed,
                        speedKmh = draft.speedKmh,
                        range = 1f..30f,
                        onSpeed = vm::setSpeed,
                    )
                    FinePrint(
                        text = strings.route.roamHint,
                        alignCenter = false,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                    )
                }

                RouteKind.IMPORTED -> {
                    if (draft.trackPoints.size >= 2) {
                        RouteLine(
                            dotColor = Gp.Green,
                            value = strings.route.importedTrackLine(
                                draft.trackName.ifBlank { strings.route.importedTrack },
                                draft.trackPoints.size,
                            ),
                            onClick = openGpx,
                        )
                        Spacer(Modifier.height(8.dp))
                        FinePrint(
                            text = strings.route.importedEndpoints(
                                draft.trackPoints.first().formatted(),
                                draft.trackPoints.last().formatted(),
                            ),
                            alignCenter = false,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    } else {
                        GpButton(
                            text = strings.route.chooseGpx,
                            onClick = openGpx,
                            style = GpButtonStyle.Outline,
                        )
                        FinePrint(
                            text = strings.route.gpxHint,
                            alignCenter = false,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    SpeedSlider(
                        title = strings.route.cruiseSpeed,
                        speedKmh = draft.speedKmh,
                        range = 5f..80f,
                        onSpeed = vm::setSpeed,
                    )
                    Spacer(Modifier.height(8.dp))
                    LoopToggles(
                        loop = draft.loop,
                        roundTrip = draft.roundTrip,
                        onLoop = vm::setLoop,
                        onRoundTrip = vm::setRoundTrip,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            FinePrint(
                text = when {
                    sessionActive -> strings.route.runningBlockedConfig
                    !draft.isComplete -> when (draft.kind) {
                        RouteKind.ROAM -> strings.route.needRoamCenter
                        RouteKind.IMPORTED -> strings.route.needGpx
                        RouteKind.POINT_TO_POINT -> strings.route.needEnd
                    }
                    else -> strings.route.preview(
                        com.gpsproxy.dev.core.geo.GeoUtils.formatDistance(distance),
                        com.gpsproxy.dev.core.geo.GeoUtils.formatDuration(durationSeconds, strings.route),
                        fullPreviewPoints.size,
                    )
                },
                alignCenter = false,
                modifier = Modifier.padding(top = 0.dp, bottom = 10.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GpIconButton(
                    icon = GpIcons.Star,
                    contentDescription = strings.route.favoriteRoute,
                    onClick = vm::saveCurrentAsFavorite,
                    background = Color(0xFFF7F9FC),
                    tint = Gp.Ink2,
                )
                GpButton(
                    text = strings.route.startRoute,
                    onClick = { vm.startRoute { started -> if (started) onStarted() } },
                    enabled = draft.isComplete && !sessionActive,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showWaypoints) {
        WaypointsDialog(
            waypoints = draft.waypoints,
            onDismiss = { showWaypoints = false },
            onAdd = {
                showWaypoints = false
                vm.prepareAddWaypoint()
                onPickWaypoint(RouteDraftStore.ADD_WAYPOINT)
            },
            onEdit = { index ->
                showWaypoints = false
                vm.prepareEditWaypoint(index)
                onPickWaypoint(index)
            },
            onRemove = vm::removeWaypoint,
            onMove = vm::moveWaypoint,
        )
    }
}

@Composable
private fun SpeedSlider(
    title: String,
    speedKmh: Int,
    range: ClosedFloatingPointRange<Float>,
    onSpeed: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Gp.Ink2, fontSize = 12.sp)
        Text("$speedKmh km/h", color = Gp.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value = speedKmh.toFloat().coerceIn(range.start, range.endInclusive),
        onValueChange = { onSpeed(it.roundToInt()) },
        valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = Gp.Blue,
            activeTrackColor = Gp.Blue,
            inactiveTrackColor = Color(0xFFE5EAF2),
        ),
    )
}

@Composable
private fun LoopToggles(
    loop: Boolean,
    roundTrip: Boolean,
    onLoop: (Boolean) -> Unit,
    onRoundTrip: (Boolean) -> Unit,
) {
    val strings = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ToggleBlock(
            title = strings.route.loopRoute,
            subtitle = strings.route.loopRouteSubtitle,
            checked = loop,
            onCheckedChange = onLoop,
            modifier = Modifier.weight(1f),
        )
        ToggleBlock(
            title = strings.route.roundTrip,
            subtitle = strings.route.roundTripSubtitle,
            checked = roundTrip,
            onCheckedChange = onRoundTrip,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RouteLine(
    dotColor: Color,
    value: String,
    placeholder: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF7F9FC))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
        Text(
            text = value,
            color = if (placeholder) Gp.Ink3 else Gp.Ink,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text(LocalStrings.current.route.tapToEdit, color = Gp.Ink3, fontSize = 10.sp)
    }
}

@Composable
private fun ToggleBlock(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Gp.Ink, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Gp.Ink3, fontSize = 10.sp, lineHeight = 14.sp)
        }
        GpSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 途经点编辑弹窗。
 *
 * 不把 8 个途经点平铺在底部卡片里：卡片会被撑出屏幕，开始按钮也会被挤走。
 * 弹窗只负责排序/删除/编辑，真正的选点仍回到地图页完成。
 */
@Composable
private fun WaypointsDialog(
    waypoints: List<RouteWaypoint>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (index: Int, delta: Int) -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.route.waypointsDialogTitle(waypoints.size, RouteDraftStore.MAX_WAYPOINTS)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                waypoints.forEachIndexed { index, waypoint ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF7F9FC))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "${index + 1}",
                            color = Gp.Blue700,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            waypoint.address.ifBlank { waypoint.point.formatted() },
                            color = Gp.Ink,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        GpIconButton(
                            icon = GpIcons.ArrowUp,
                            contentDescription = strings.route.moveUp,
                            onClick = { onMove(index, -1) },
                            size = 24.dp,
                            background = Color.White,
                            tint = Gp.Ink3,
                        )
                        GpIconButton(
                            icon = GpIcons.ArrowDown,
                            contentDescription = strings.route.moveDown,
                            onClick = { onMove(index, 1) },
                            size = 24.dp,
                            background = Color.White,
                            tint = Gp.Ink3,
                        )
                        GpIconButton(
                            icon = GpIcons.Edit,
                            contentDescription = strings.route.editWaypoint,
                            onClick = { onEdit(index) },
                            size = 24.dp,
                            background = Color.White,
                            tint = Gp.Ink3,
                        )
                        GpIconButton(
                            icon = GpIcons.Trash,
                            contentDescription = strings.route.deleteWaypoint,
                            onClick = { onRemove(index) },
                            size = 24.dp,
                            background = Color.White,
                            tint = Gp.Red,
                        )
                    }
                }
                if (waypoints.isEmpty()) {
                    FinePrint(strings.route.noWaypoints, alignCenter = false)
                }
                TextButton(
                    onClick = onAdd,
                    enabled = waypoints.size < RouteDraftStore.MAX_WAYPOINTS,
                ) {
                    Text(
                        if (waypoints.size >= RouteDraftStore.MAX_WAYPOINTS) {
                            strings.route.limitReached
                        } else {
                            strings.route.addWaypointButton
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.route.done) }
        },
    )
}
