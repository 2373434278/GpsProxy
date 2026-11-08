package com.gpsproxy.dev.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsproxy.dev.domain.simulation.model.ChannelStatus
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp
import kotlinx.coroutines.delay

/**
 * 全局消息渲染宿主。
 *
 * 消息来自 [com.gpsproxy.dev.core.messaging.Messenger] 端口；界面只负责
 * 收集一次并渲染，不再允许各屏幕直接调用全局单例（那会让"谁在什么条件下
 * 弹了什么"无法追踪，也无法测试）。
 */

enum class StatusTone { Idle, Active, Warn, Error }

@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
) {
    val color = when (tone) {
        StatusTone.Active -> Gp.Green
        StatusTone.Warn -> Gp.Orange
        StatusTone.Error -> Gp.Red
        StatusTone.Idle -> Color(0xFF667085)
    }
    val led = when (tone) {
        StatusTone.Active -> Gp.Green
        StatusTone.Warn -> Gp.Orange
        StatusTone.Error -> Gp.Red
        StatusTone.Idle -> Gp.Ink3
    }
    Surface(
        color = Color.White.copy(alpha = .97f),
        shape = CircleShape,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(led)
            )
            Text(
                text = text,
                color = color,
                fontSize = if (mono) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFF4F6FA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFFB6C0CE), modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = Gp.Ink2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            color = Gp.Ink3,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun ErrorCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Gp.Red50)
            .border(1.dp, Color(0xFFFFD5D2), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(title, color = Color(0xFFB42318), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = Color(0xFF7A271A), fontSize = 11.5.sp, lineHeight = 18.sp)
        if (action != null) {
            Spacer(Modifier.height(10.dp))
            action()
        }
    }
}

@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Gp.Blue50,
    border: Color = Gp.Blue100,
    textColor: Color = Color(0xFF1849A9),
    title: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (title != null) {
            Text(title, color = textColor, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
        }
        Text(text, color = textColor, fontSize = 11.5.sp, lineHeight = 18.sp)
    }
}

@Composable
fun FinePrint(text: String, modifier: Modifier = Modifier, alignCenter: Boolean = true) {
    Text(
        text = text,
        color = Gp.Ink3,
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
        textAlign = if (alignCenter) TextAlign.Center else TextAlign.Start,
        modifier = modifier.fillMaxWidth().padding(top = 14.dp),
    )
}

/** 统一的 1dp 分隔线，避免各页手写 Box + background。 */

@Composable
fun <T> GpOptionDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    val strings = LocalStrings.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(value) }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, color = Gp.Ink, fontSize = 14.sp)
                        if (value == selected) {
                            Icon(GpIcons.Check, contentDescription = null, tint = Gp.Blue)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(strings.common.cancel) }
        },
    )
}

@Composable
fun StatusDot(status: ChannelStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        ChannelStatus.OK -> Gp.Green
        ChannelStatus.WARN -> Gp.Orange
        ChannelStatus.BAD -> Gp.Red
        ChannelStatus.OFF -> Gp.Ink3
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}
