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

@Composable
fun MessageHost(messenger: com.gpsproxy.dev.core.messaging.Messenger, modifier: Modifier = Modifier) {
    val message by messenger.messages.collectAsStateWithLifecycle(initialValue = null)
    var visible by remember { mutableStateOf<com.gpsproxy.dev.core.messaging.Message?>(null) }
    LaunchedEffect(message?.id) {
        val incoming = message ?: return@LaunchedEffect
        visible = incoming
        delay(1900)
        if (visible?.id == incoming.id) visible = null
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible != null,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            val toneColor = when (visible?.tone) {
                com.gpsproxy.dev.core.messaging.MessageTone.ERROR -> Color(0xFFB42318)
                com.gpsproxy.dev.core.messaging.MessageTone.WARN -> Color(0xFFB54708)
                com.gpsproxy.dev.core.messaging.MessageTone.SUCCESS -> Color(0xFF067647)
                else -> Color.White
            }
            Surface(
                color = Color(0xF21B212B),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(bottom = 96.dp, start = 24.dp, end = 24.dp),
            ) {
                Text(
                    text = visible?.text.orEmpty(),
                    color = toneColor,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
