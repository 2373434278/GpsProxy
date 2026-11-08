package com.gpsproxy.dev.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.ui.feature.route.RouteRunningViewModel
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.components.GpDivider
import com.gpsproxy.dev.ui.components.BallPanelOverlay
import com.gpsproxy.dev.ui.components.FloatingBallLayer
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpIconButton
import com.gpsproxy.dev.ui.components.GpProgressBar
import com.gpsproxy.dev.ui.components.MetricsRow
import com.gpsproxy.dev.ui.components.StatusChip
import com.gpsproxy.dev.ui.components.StatusTone
import com.gpsproxy.dev.ui.components.SurfaceCard
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.map.SchematicMap
import com.gpsproxy.dev.ui.map.rememberMapCamera
import com.gpsproxy.dev.ui.theme.Gp
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RouteRunningScreen(
    vm: RouteRunningViewModel,
    onBackHome: () -> Unit,
    onFavorites: () -> Unit,
    onPick: () -> Unit,
) {
    val simulation by vm.simulation.collectAsStateWithLifecycle()
    val route = simulation.route
    val point = simulation.point ?: route?.start ?: com.gpsproxy.dev.domain.place.DefaultLocation.POINT
    val markerPoint = simulation.injectedPoint ?: point
    val routePoints = remember(simulation.routePathPoints, simulation.blurRadiusMeters) {
        if (route != null) {
            // 使用引擎实际规划的轨迹（真实路网或内置曲线），不再由界面重复合成。
            val base = simulation.routePathPoints.ifEmpty {
                GeoUtils.buildSyntheticRoute(route.start, route.end)
            }
            // 模糊是整条轨迹的固定平移，偏移量取「注入点 - 真实点」，
            // 与控制器内部保持一致，路线不会和标记错位。
            val injected = simulation.injectedPoint
            if (simulation.blurRadiusMeters > 0 && base.isNotEmpty() && injected != null) {
                val (north, east) = GeoUtils.projectMeters(point, injected)
                base.map { GeoUtils.offsetMeters(it, north, east) }
            } else {
                base
            }
        } else {
            emptyList()
        }
    }
    val camera = rememberMapCamera(markerPoint, 15f)
    // 20k 点的导入轨迹不适合逐帧全量绘制；仿真走完整轨迹，地图只画形状。
    val mapRoutePoints = remember(routePoints) { GeoUtils.downsample(routePoints, 2_000) }
    var showPanel by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var speedFactor by remember(simulation.speedFactor) { mutableStateOf(simulation.speedFactor) }
    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        uri?.let { vm.exportGpx(it.toString()) }
    }

    val strings = LocalStrings.current
    val progress = simulation.progress
    val pct = ((progress?.fraction ?: 0f) * 100f).roundToInt()
    val doneMeters = progress?.distanceDoneM ?: 0.0
    val totalMeters = progress?.distanceTotalM ?: 0.0
    val completed = simulation.runState == RunState.COMPLETED
    val paused = simulation.runState == RunState.PAUSED
    val (tone, statusText) = when {
        simulation.runState == RunState.ERROR -> StatusTone.Error to strings.route.statusError
        completed -> StatusTone.Active to strings.route.statusCompleted
        paused -> StatusTone.Warn to strings.route.statusPaused
        simulation.mode == SimMode.ROUTE -> StatusTone.Active to strings.route.statusRunning(
            route?.speedKmh ?: 0,
            "%.2f".format(simulation.speedFactor),
        )
        else -> StatusTone.Idle to strings.route.statusIdle
    }

    Box(Modifier.fillMaxSize()) {
        SchematicMap(
            modifier = Modifier.fillMaxSize(),
            camera = camera,
            marker = markerPoint,
            routePoints = mapRoutePoints,
            dashedRoute = false,
            accuracyMeters = simulation.accuracy.toFloat(),
            followMarker = true,
            watermark = strings.route.watermarkRunning(
                simulation.routeSource?.let { strings.route.routeSourceLabel(it.name) }
                    ?: strings.route.genericTrack,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusChip(text = statusText, tone = tone)
            simulation.routeSource?.let {
                StatusChip(text = strings.route.routeSourceLabel(it.name), tone = StatusTone.Idle, mono = true)
            }
            if (simulation.autoStopAtMillis > 0) {
                StatusChip(
                    text = strings.route.stopAt(formatClock(simulation.autoStopAtMillis)),
                    tone = StatusTone.Idle,
                    mono = true,
                )
            }
            Spacer(Modifier.weight(1f))
            GpIconButton(
                icon = GpIcons.Download,
                contentDescription = strings.route.exportGpx,
                onClick = { exportPicker.launch(defaultGpxName()) },
                background = Color.White,
                tint = Gp.Ink,
            )
            GpIconButton(
                icon = GpIcons.Back,
                contentDescription = strings.route.backHome,
                onClick = onBackHome,
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
            shape = RoundedCornerShape(20.dp),
            elevation = 20.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$pct",
                    color = Gp.Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "%",
                    color = Gp.Ink3,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        strings.route.walked(GeoUtils.formatDistance(doneMeters)),
                        color = Gp.Ink2,
                        fontSize = 11.5.sp,
                    )
                    Text(
                        if (completed) {
                            strings.route.arrived
                        } else {
                            strings.route.remaining(
                                GeoUtils.formatDuration(progress?.remainingSeconds ?: 0, strings.route),
                            )
                        },
                        color = Gp.Ink3,
                        fontSize = 11.5.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            GpProgressBar(progress?.fraction ?: 0f)
            if (!completed) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.route.dragToSeek, color = Gp.Ink2, fontSize = 11.sp)
                    Text(
                        "${(((scrubFraction ?: progress?.fraction ?: 0f) * 100f)).roundToInt()}%",
                        color = Gp.Ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value = scrubFraction ?: (progress?.fraction ?: 0f),
                    onValueChange = { scrubFraction = it },
                    onValueChangeFinished = {
                        scrubFraction?.let(vm::seek)
                        scrubFraction = null
                    },
                    valueRange = 0f..0.999f,
                    colors = SliderDefaults.colors(
                        thumbColor = Gp.Blue,
                        activeTrackColor = Gp.Blue,
                        inactiveTrackColor = Color(0xFFE5EAF2),
                    ),
                )
            }
            simulation.routeDegradeReason?.let { reason ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.route.routeDegraded(reason),
                    color = Gp.Ink3,
                    fontSize = 10.5.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            GpDivider()
            MetricsRow(
                items = listOf(
                    "${simulation.speedKmh.roundToInt()}" to "km/h",
                    "${progress?.totalPoints ?: routePoints.size}" to strings.route.metricPoints,
                    String.format(Locale.US, "%.1f°", simulation.bearing) to strings.route.metricBearing,
                    "±${simulation.accuracy} m" to strings.route.metricAccuracy,
                ),
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (!completed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.route.speedFactor, color = Gp.Ink2, fontSize = 11.sp)
                    Text(
                        String.format(Locale.US, "%.2fx", speedFactor),
                        color = Gp.Ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value = speedFactor.coerceIn(0.5f, 4f),
                    onValueChange = { speedFactor = it },
                    onValueChangeFinished = { vm.setSpeedFactor(speedFactor) },
                    valueRange = 0.5f..4f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = Gp.Blue,
                        activeTrackColor = Gp.Blue,
                        inactiveTrackColor = Color(0xFFE5EAF2),
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GpButton(
                        text = strings.route.reverse,
                        onClick = vm::reverse,
                        style = GpButtonStyle.Outline,
                        modifier = Modifier.weight(1f),
                    )
                    GpButton(
                        text = if (paused) strings.route.resume else strings.route.pause,
                        onClick = vm::pauseOrResume,
                        style = GpButtonStyle.Outline,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                GpButton(
                    text = strings.route.stop,
                    onClick = {
                        vm.stopRoute()
                        onBackHome()
                    },
                    style = GpButtonStyle.Danger,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GpButton(
                        text = strings.route.replay,
                        onClick = vm::restart,
                        style = GpButtonStyle.Outline,
                        modifier = Modifier.weight(1f),
                    )
                    GpButton(
                        text = strings.route.backHome,
                        onClick = onBackHome,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = strings.route.footer(
                    GeoUtils.formatDistance(totalMeters),
                    progress?.pointIndex ?: 0,
                    progress?.totalPoints ?: routePoints.size,
                ) + if (simulation.autoStopAtMillis > 0) {
                    strings.route.autoStopSuffix(formatClock(simulation.autoStopAtMillis))
                } else {
                    ""
                },
                color = Gp.Ink3,
                fontSize = 10.5.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }

        FloatingBallLayer(initialBottomPadding = 380.dp) { showPanel = true }

        if (showPanel) {
            BallPanelOverlay(
                paused = paused,
                onDismiss = { showPanel = false },
                onFavorites = {
                    showPanel = false
                    onFavorites()
                },
                onPick = {
                    showPanel = false
                    onPick()
                },
                onPauseResume = vm::pauseOrResume,
                onStop = {
                    showPanel = false
                    vm.stopRoute()
                    onBackHome()
                },
                onNudge = vm::nudge,
            )
        }
    }
}

/** 自动停止到期的本地时间（只用于展示，不做倒计时轮询）。 */
private fun formatClock(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

/** 导出文件的默认名：带时间戳，避免覆盖上一次导出。 */
private fun defaultGpxName(): String =
    "gpsproxy-" + java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")) + ".gpx"
