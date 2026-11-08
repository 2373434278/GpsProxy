package com.gpsproxy.dev.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsproxy.dev.core.device.ContainerSupport
import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.ui.feature.container.ContainerViewModel
import com.gpsproxy.dev.domain.selfcheck.StepState
import com.gpsproxy.dev.ui.components.FinePrint
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpPage
import com.gpsproxy.dev.ui.components.GpProgressBar
import com.gpsproxy.dev.ui.components.InfoBanner
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp

@Composable
fun ContainerInitScreen(
    vm: ContainerViewModel,
    onBack: () -> Unit,
    onApps: () -> Unit,
) {
    val steps by vm.steps.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val available by vm.available.collectAsStateWithLifecycle()
    val capability = vm.capability
    val strings = LocalStrings.current

    LaunchedEffect(Unit) {
        if (steps.isEmpty()) vm.runInit()
    }

    GpPage(title = strings.container.initTitle, onBack = onBack) {
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4DA3FF), Gp.Blue)))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GpIcons.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = when {
                running -> strings.container.initInitializing
                available -> strings.container.initReady
                else -> strings.container.initUnavailable
            },
            color = Gp.Ink,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = strings.container.initSubtitle,
            color = Gp.Ink3,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        Column {
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StepBadge(index = index, state = step.state)
                    Column(Modifier.weight(1f)) {
                        Text(step.title, color = Gp.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(step.detail, color = Gp.Ink3, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        val doneCount = steps.count { it.state == StepState.DONE || it.state == StepState.WARN }
        GpProgressBar(if (steps.isEmpty()) 0f else doneCount / steps.size.toFloat())
        FinePrint(
            text = if (steps.isEmpty()) {
                strings.container.preparing
            } else {
                strings.container.checksDone(doneCount, steps.size)
            },
            alignCenter = false,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(16.dp))
        if (capability.containerSupport == ContainerSupport.EXPERIMENTAL) {
            InfoBanner(
                title = strings.container.experimentalTitle,
                text = strings.container.experimentalText,
                tint = Gp.Orange50,
                border = Color(0xFFFFE4C2),
                textColor = Color(0xFFB54708),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (error != null) {
            InfoBanner(
                title = "${ErrorCode.BOX_0101.code} · ${strings.error.title(ErrorCode.BOX_0101)}",
                text = strings.error.cause(ErrorCode.BOX_0101) + "\n" + strings.error.action(ErrorCode.BOX_0101),
                tint = Gp.Red50,
                border = Color(0xFFFFD5D2),
                textColor = Color(0xFFB42318),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        GpButton(
            text = strings.container.manageApps,
            onClick = onApps,
            enabled = !running && available,
        )
        Spacer(Modifier.height(8.dp))
        GpButton(
            text = strings.container.recheck,
            onClick = { vm.runInit() },
            style = GpButtonStyle.Ghost,
            enabled = !running,
            height = 44.dp,
        )
        Spacer(Modifier.height(12.dp))
    }
}