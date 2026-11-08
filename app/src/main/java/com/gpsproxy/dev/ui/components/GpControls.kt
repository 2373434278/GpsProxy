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

enum class GpButtonStyle { Primary, Outline, Danger, Ghost }

@Composable
fun GpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GpButtonStyle = GpButtonStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 48.dp,
) {
    val shape = RoundedCornerShape(14.dp)
    val background: Brush = when {
        !enabled -> SolidColor(Color(0xFFD9E1EB))
        style == GpButtonStyle.Primary -> Brush.linearGradient(
            listOf(Color(0xFF3189FF), Gp.Blue)
        )
        style == GpButtonStyle.Danger -> SolidColor(Gp.Red50)
        style == GpButtonStyle.Outline -> SolidColor(Color.White)
        else -> SolidColor(Color.Transparent)
    }
    val textColor = when {
        !enabled -> Color(0xFF98A2B3)
        style == GpButtonStyle.Primary -> Color.White
        style == GpButtonStyle.Danger -> Gp.Red
        style == GpButtonStyle.Outline -> Color(0xFF344054)
        else -> Gp.Ink3
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (style == GpButtonStyle.Outline) {
                    Modifier.border(BorderStroke(1.dp, Gp.Line2), shape)
                } else {
                    Modifier
                }
            )
            .then(
                if (style == GpButtonStyle.Primary && enabled) {
                    Modifier.shadow(10.dp, shape, ambientColor = Gp.Blue.copy(alpha = .3f), spotColor = Gp.Blue.copy(alpha = .3f))
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun GpIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFFF3F5F9),
    tint: Color = Color(0xFF344054),
    size: Dp = 36.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun GpChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(if (selected) Gp.Blue50 else Color(0xFFF3F5F9))
            .then(if (selected) Modifier.border(1.dp, Gp.Blue100, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Gp.Blue700 else Gp.Ink2,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
fun GpSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF3F5F9))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .then(
                        if (selected) {
                            Modifier
                                .shadow(2.dp, RoundedCornerShape(9.dp))
                                .background(Color.White)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) Gp.Ink else Gp.Ink2,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun GpSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Gp.Blue,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFDCE3EC),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun GpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    isError: Boolean = false,
    height: Dp = 46.dp,
    textStyle: TextStyle = TextStyle(fontSize = 13.sp, color = Gp.Ink),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xFFF7F9FC))
            .border(1.dp, if (isError) Gp.Red else Gp.Line, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = Gp.Ink3, modifier = Modifier.size(16.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(Gp.Blue),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = Gp.Ink3, fontSize = 13.sp)
                }
                inner()
            },
        )
        if (trailing != null) trailing()
    }
}

@Composable
fun ValuePill(text: String, onClick: (() -> Unit)? = null, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFFF3F5F9))
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (enabled) Gp.Ink2 else Gp.Ink3,
            fontSize = 12.sp,
            fontFamily = FontFamily.Default,
        )
    }
}

@Composable
fun CheckBox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(19.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) Gp.Blue else Color.Transparent)
            .border(1.5.dp, if (checked) Gp.Blue else Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(GpIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}
