package com.gpsproxy.dev.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsproxy.dev.core.device.PermissionUtils
import com.gpsproxy.dev.domain.simulation.usecase.StartBlockAction
import com.gpsproxy.dev.ui.components.GpDivider
import com.gpsproxy.dev.ui.components.BallPanelOverlay
import com.gpsproxy.dev.ui.components.FloatingBallLayer
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpIconButton
import com.gpsproxy.dev.ui.components.MetricsRow
import com.gpsproxy.dev.ui.components.StatusChip
import com.gpsproxy.dev.ui.components.StatusTone
import com.gpsproxy.dev.ui.components.SurfaceCard
import com.gpsproxy.dev.ui.feature.home.HomeStatus
import com.gpsproxy.dev.ui.feature.home.HomeUiState
import com.gpsproxy.dev.ui.feature.home.HomeViewModel
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.map.SchematicMap
import com.gpsproxy.dev.ui.map.rememberMapCamera
import com.gpsproxy.dev.ui.theme.Gp
import java.util.Locale

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onSearch: () -> Unit,
    onPick: () -> Unit,
    onFavorites: () -> Unit,
    onRoute: () -> Unit,
    onApps: () -> Unit,
    onContainerApps: () -> Unit,
    onContainerInit: () -> Unit,
    onSettings: () -> Unit,
    onPermissions: () -> Unit,
    onRunning: () -> Unit,
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val blockAction by vm.blockAction.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val camera = rememberMapCamera(ui.markerPoint)
    LaunchedEffect(ui.markerPoint) { camera.center = ui.markerPoint }
    LaunchedEffect(Unit) { vm.refresh() }

    var showPanel by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        SchematicMap(
            modifier = Modifier.fillMaxSize(),
            camera = camera,
            marker = ui.markerPoint,
            accuracyMeters = if (ui.injected) ui.accuracyMeters.toFloat() else null,
            onCenterChanged = vm::onMapCenterChanged,
            watermark = strings.home.mapWatermarkHome,
        )

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(text = statusText(ui.status), tone = ui.statusTone)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ui.blurEnabled) {
                        StatusChip(
                            text = strings.home.blurChip(ui.blurRadiusMeters),
                            tone = StatusTone.Warn,
                            mono = true,
                        )
                    }
                    StatusChip(text = ui.coordLabel, tone = StatusTone.Idle, mono = true)
                    GpIconButton(
                        icon = GpIcons.Shield,
                        contentDescription = strings.home.permissions,
                        onClick = onPermissions,
                        background = Color.White,
                        tint = Gp.Ink,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SearchPill(onClick = onSearch)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 150.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickButton(GpIcons.Star, strings.home.quickFavorites, onFavorites)
            QuickButton(GpIcons.Route, strings.home.quickRoute, onRoute)
            QuickButton(GpIcons.Grid, strings.home.quickApps, onApps)
            QuickButton(GpIcons.Settings, strings.home.quickSettings, onSettings)
        }

        SurfaceCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 14.dp),
            elevation = 20.dp,
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = ui.address,
                        color = Gp.Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 21.sp,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${ui.displayCoords} · ±${ui.accuracyMeters} m",
                        color = Gp.Ink3,
                        fontSize = 11.5.sp,
                    )
                    if (ui.blurRadiusMeters > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = strings.home.blurDetail(ui.blurRadiusMeters, vm.displayCoords(ui.point)),
                            color = Gp.Orange,
                            fontSize = 10.5.sp,
                        )
                    }
                    if (ui.autoStopAtMillis > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = strings.home.autoStopAt(formatClock(ui.autoStopAtMillis)),
                            color = Gp.Orange,
                            fontSize = 10.5.sp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    GpIconButton(
                        icon = GpIcons.Map,
                        contentDescription = strings.home.externalMap,
                        onClick = vm::openInExternalMap,
                        tint = Gp.Ink2,
                    )
                    GpIconButton(
                        icon = GpIcons.Copy,
                        contentDescription = strings.home.copyCoords,
                        onClick = {
                            clipboard.setText(AnnotatedString(ui.displayCoords))
                            vm.notifyCopied()
                        },
                        tint = Gp.Ink2,
                    )
                    GpIconButton(
                        icon = if (ui.isFavorite) GpIcons.StarFilled else GpIcons.Star,
                        contentDescription = strings.home.favorite,
                        onClick = vm::toggleFavoriteCurrent,
                        tint = if (ui.isFavorite) Gp.Blue else Gp.Ink2,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            GpDivider()
            Spacer(Modifier.height(6.dp))
            MetricsRow(
                items = listOf(
                    "±${ui.accuracyMeters} m" to strings.home.metricAccuracy,
                    String.format(Locale.US, "%.1f km/h", ui.speedKmh) to strings.home.metricSpeed,
                    String.format(Locale.US, "%.0f m", ui.altitudeMeters) to strings.home.metricAltitude,
                    "${ui.okChannels} / ${ui.channelTotal}" to strings.home.metricChannels,
                ),
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ui.isRoute) {
                    GpButton(
                        text = strings.home.viewRoute,
                        onClick = onRunning,
                        style = GpButtonStyle.Outline,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    GpButton(
                        text = strings.home.pickOnMap,
                        onClick = onPick,
                        style = GpButtonStyle.Outline,
                        modifier = Modifier.weight(1f),
                    )
                }
                GpButton(
                    text = if (ui.injected) strings.home.stopSimulation else strings.home.startSimulation,
                    onClick = {
                        if (ui.injected) vm.stopSimulation() else vm.startSimulation()
                    },
                    style = if (ui.injected) GpButtonStyle.Danger else GpButtonStyle.Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        FloatingBallLayer(initialBottomPadding = 310.dp) { showPanel = true }

        if (showPanel) {
            BallPanelOverlay(
                paused = ui.paused,
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
                    vm.stopSimulation()
                },
                onNudge = vm::nudge,
                walkEnabled = ui.injected && !ui.isRoute && !ui.paused,
                walkSpeedKmh = ui.walkSpeedKmh,
                onWalk = vm::walk,
                onWalkStop = vm::stopWalk,
                onWalkSpeedChange = vm::setWalkSpeed,
            )
        }
    }

    if (blockAction != null) {
        SetupRequiredDialog(
            action = blockAction!!,
            onDismiss = { vm.consumeBlockAction() },
            onOpenApps = {
                vm.consumeBlockAction()
                onContainerApps()
            },
            onOpenContainerInit = {
                vm.consumeBlockAction()
                onContainerInit()
            },
            onRetry = {
                vm.consumeBlockAction()
                vm.startSimulation()
            },
            onOpenDeveloperOptions = {
                vm.consumeBlockAction()
                PermissionUtils.openDeveloperOptions(context)
            },
            onOpenPermissions = {
                vm.consumeBlockAction()
                onPermissions()
            },
        )
    }
}

/**
 * 启动被阻断时的引导弹窗。
 *
 * 弹窗内容由 [StartBlockAction] 决定，而不是由界面自己猜测"缺什么"：
 * 同一条规则在首页、巡航页、收藏页共享。
 */
@Composable
private fun SetupRequiredDialog(
    action: StartBlockAction,
    onDismiss: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenContainerInit: () -> Unit,
    onRetry: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val strings = LocalStrings.current
    val title: String
    val message: String
    when (action) {
        StartBlockAction.OPEN_CONTAINER_INIT -> {
            title = strings.home.blockContainerInitTitle
            message = strings.home.blockContainerInitMessage
        }

        StartBlockAction.RETRY -> {
            title = strings.home.blockRetryTitle
            message = strings.home.blockRetryMessage
        }

        StartBlockAction.OPEN_CONTAINER_APPS -> {
            title = strings.home.blockContainerAppsTitle
            message = strings.home.blockContainerAppsMessage
        }

        StartBlockAction.OPEN_DEVELOPER_OPTIONS,
        StartBlockAction.OPEN_PERMISSIONS -> {
            title = strings.home.blockPermissionsTitle
            message = strings.home.blockPermissionsMessage
        }

        StartBlockAction.NONE -> {
            title = strings.home.blockNoneTitle
            message = strings.home.blockNoneMessage
        }
    }
    val confirmText = when (action) {
        StartBlockAction.OPEN_CONTAINER_APPS -> strings.home.blockConfirmImportApps
        StartBlockAction.OPEN_CONTAINER_INIT -> strings.home.blockConfirmContainerInit
        StartBlockAction.RETRY -> strings.common.retry
        StartBlockAction.OPEN_DEVELOPER_OPTIONS -> strings.home.blockConfirmDeveloperOptions
        StartBlockAction.OPEN_PERMISSIONS -> strings.home.blockConfirmPermissions
        StartBlockAction.NONE -> strings.home.blockConfirmOk
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    when (action) {
                        StartBlockAction.OPEN_CONTAINER_APPS -> onOpenApps()
                        StartBlockAction.OPEN_CONTAINER_INIT -> onOpenContainerInit()
                        StartBlockAction.RETRY -> onRetry()
                        StartBlockAction.OPEN_DEVELOPER_OPTIONS -> onOpenDeveloperOptions()
                        StartBlockAction.OPEN_PERMISSIONS -> onOpenPermissions()
                        StartBlockAction.NONE -> onDismiss()
                    }
                }
            ) { Text(confirmText) }
        },
        dismissButton = {
            // 重试失败后仍需要一条去容器空间看自检的退路，不能只留"稍后"。
            val toContainer = action == StartBlockAction.RETRY
            TextButton(
                onClick = {
                    if (toContainer) onOpenContainerInit() else onDismiss()
                },
            ) { Text(if (toContainer) strings.home.blockDismissContainer else strings.home.blockDismissLater) }
        },
    )
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 8.dp,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(GpIcons.Search, contentDescription = null, tint = Gp.Ink3, modifier = Modifier.size(17.dp))
            Text(LocalStrings.current.home.searchHint, color = Gp.Ink3, fontSize = 13.5.sp)
        }
    }
}

@Composable
private fun QuickButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = .97f),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 8.dp,
        onClick = onClick,
        modifier = Modifier.size(42.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color(0xFF344054), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun statusText(status: HomeStatus): String {
    val home = LocalStrings.current.home
    return when (status) {
        HomeStatus.NOT_STARTED -> home.statusNotStarted
        HomeStatus.PAUSED -> home.statusPaused
        HomeStatus.ROUTE_COMPLETED -> home.statusRouteCompleted
        HomeStatus.CHANNEL_MISS -> home.statusChannelMiss
        HomeStatus.ROUTE_ACTIVE -> home.statusRouteActive
        HomeStatus.WALKING -> home.statusWalking
        HomeStatus.ACTIVE -> home.statusActive
    }
}

/** 自动停止到期的本地时间（只用于展示，不做倒计时轮询）。 */
private fun formatClock(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
