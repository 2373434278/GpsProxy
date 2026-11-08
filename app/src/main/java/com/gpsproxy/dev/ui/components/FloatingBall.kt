package com.gpsproxy.dev.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp
import kotlin.math.roundToInt

@Composable
fun FloatingBallLayer(
    modifier: Modifier = Modifier,
    initialBottomPadding: Dp = 300.dp,
    onClick: () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val strings = LocalStrings.current
        val density = LocalDensity.current
        val ballPx = with(density) { 52.dp.toPx() }
        val marginPx = with(density) { 10.dp.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        var position by remember {
            mutableStateOf(
                Offset(
                    x = maxW - ballPx - marginPx,
                    y = (maxH - with(density) { initialBottomPadding.toPx() } - ballPx)
                        .coerceAtLeast(marginPx),
                )
            )
        }
        var dragging by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .size(52.dp)
                .offset {
                    IntOffset(position.x.roundToInt(), position.y.roundToInt())
                }
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = Gp.Blue.copy(alpha = .5f),
                    spotColor = Gp.Blue.copy(alpha = .5f),
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF54A8FF), Gp.Blue)))
                .border(1.dp, Color.White.copy(alpha = .45f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            val snappedX = if (position.x + ballPx / 2 < maxW / 2) {
                                marginPx
                            } else {
                                maxW - ballPx - marginPx
                            }
                            position = position.copy(x = snappedX)
                        },
                        onDragCancel = { dragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            position = Offset(
                                x = (position.x + dragAmount.x).coerceIn(0f, (maxW - ballPx).coerceAtLeast(0f)),
                                y = (position.y + dragAmount.y).coerceIn(0f, (maxH - ballPx).coerceAtLeast(0f)),
                            )
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (!dragging) onClick() }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                GpIcons.Crosshair,
                contentDescription = strings.home.overlayContentDescription,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
