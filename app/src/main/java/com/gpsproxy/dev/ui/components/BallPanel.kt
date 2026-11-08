package com.gpsproxy.dev.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun BallPanelOverlay(
    paused: Boolean,
    onDismiss: () -> Unit,
    onFavorites: () -> Unit,
    onPick: () -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onNudge: (north: Double, east: Double) -> Unit,
    walkEnabled: Boolean = false,
    walkSpeedKmh: Int = 5,
    onWalk: (bearingDeg: Float, speedMps: Float) -> Unit = { _, _ -> },
    onWalkStop: () -> Unit = {},
    onWalkSpeedChange: (Int) -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .10f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )
        BallPanelCard(
            paused = paused,
            onClose = onDismiss,
            onFavorites = onFavorites,
            onPick = onPick,
            onPauseResume = onPauseResume,
            onStop = onStop,
            onNudge = onNudge,
            walkEnabled = walkEnabled,
            walkSpeedKmh = walkSpeedKmh,
            onWalk = onWalk,
            onWalkStop = onWalkStop,
            onWalkSpeedChange = onWalkSpeedChange,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 96.dp),
        )
    }
}

@Composable
fun BallPanelCard(
    paused: Boolean,
    onClose: () -> Unit,
    onFavorites: () -> Unit,
    onPick: () -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onNudge: (north: Double, east: Double) -> Unit,
    modifier: Modifier = Modifier,
    walkEnabled: Boolean = false,
    walkSpeedKmh: Int = 5,
    onWalk: (bearingDeg: Float, speedMps: Float) -> Unit = { _, _ -> },
    onWalkStop: () -> Unit = {},
    onWalkSpeedChange: (Int) -> Unit = {},
) {
    val strings = LocalStrings.current
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 16.dp,
        modifier = modifier.width(250.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(strings.home.overlayTitle, color = Gp.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Gp.Blue50)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(strings.home.overlayBadge, color = Gp.Blue700, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    GpIconButton(
                        icon = GpIcons.Close,
                        contentDescription = strings.common.close,
                        onClick = onClose,
                        size = 26.dp,
                        background = Color(0xFFF3F5F9),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelAction(
                    icon = GpIcons.Star,
                    label = strings.home.favoritePoint,
                    onClick = onFavorites,
                    modifier = Modifier.weight(1f),
                )
                PanelAction(
                    icon = GpIcons.Pin,
                    label = strings.home.repick,
                    onClick = onPick,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelAction(
                    icon = if (paused) GpIcons.Play else GpIcons.Pause,
                    label = if (paused) strings.home.resumeInjection else strings.home.pauseInjection,
                    onClick = onPauseResume,
                    modifier = Modifier.weight(1f),
                )
                PanelAction(
                    icon = GpIcons.Stop,
                    label = strings.home.stopSimulation,
                    onClick = onStop,
                    danger = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (walkEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(strings.home.walkSpeed, color = Gp.Ink2, fontSize = 11.sp)
                    Text(
                        "$walkSpeedKmh km/h",
                        color = Gp.Ink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value = walkSpeedKmh.toFloat().coerceIn(1f, 20f),
                    onValueChange = { onWalkSpeedChange(it.roundToInt()) },
                    valueRange = 1f..20f,
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = Gp.Blue,
                        activeTrackColor = Gp.Blue,
                        inactiveTrackColor = Color(0xFFE5EAF2),
                    ),
                )
            }
            Joystick(
                enabled = walkEnabled,
                walkSpeedKmh = walkSpeedKmh,
                onNudge = onNudge,
                onWalk = onWalk,
                onWalkStop = onWalkStop,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = if (walkEnabled) strings.home.walkHintHold else strings.home.walkHintDrag,
                color = Gp.Ink3,
                fontSize = 10.5.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PanelAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val border = if (danger) Color(0xFFFFD9D6) else Gp.Line
    val background = if (danger) Color(0xFFFFF8F7) else Color(0xFFFAFBFD)
    val tint = if (danger) Gp.Red else Color(0xFF475467)
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, color = if (danger) Gp.Red else Gp.Ink2, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Joystick(
    enabled: Boolean,
    walkSpeedKmh: Int,
    onNudge: (Double, Double) -> Unit,
    onWalk: (Float, Float) -> Unit,
    onWalkStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val maxOffset = 25f
    Box(
        modifier = modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(Color(0xFFF3F6FA))
            .border(1.dp, Gp.Line, CircleShape)
            .pointerInput(enabled, walkSpeedKmh) {
                detectDragGestures(
                    onDragEnd = {
                        knob = Offset.Zero
                        if (enabled) onWalkStop()
                    },
                    onDragCancel = {
                        knob = Offset.Zero
                        if (enabled) onWalkStop()
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val next = Offset(
                            (knob.x + drag.x).coerceIn(-maxOffset, maxOffset),
                            (knob.y + drag.y).coerceIn(-maxOffset, maxOffset),
                        )
                        knob = next
                        if (enabled) {
                            // 屏幕坐标 → 方位角：上为北（0°），右为东（90°）。
                            val bearing = (
                                Math.toDegrees(atan2(next.x.toDouble(), -next.y.toDouble()))
                                    .toFloat() + 360f
                                ) % 360f
                            val magnitude = (sqrt(next.x * next.x + next.y * next.y) / maxOffset)
                                .coerceIn(0f, 1f)
                            onWalk(bearing, magnitude * walkSpeedKmh / 3.6f)
                        } else {
                            onNudge(-drag.y * 0.5, drag.x * 0.5)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0xFFE7EDF6), CircleShape)
        )
    }
}
