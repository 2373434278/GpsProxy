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
import androidx.compose.material3.CircularProgressIndicator
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
import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.ui.feature.startup.StartupViewModel
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
fun EngineScreen(
    vm: StartupViewModel,
    onDone: () -> Unit,
    onUnsupported: () -> Unit,
    onContainerInit: () -> Unit,
) {
    val steps by vm.steps.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val capability = vm.capability
    val containerAvailable by vm.containerAvailable.collectAsStateWithLifecycle()
    val engineError = if (capability.supported) null else ErrorCode.ENG_0102
    val strings = LocalStrings.current

    LaunchedEffect(Unit) {
        vm.runSelfCheckIfIdle()
    }

    GpPage(title = strings.onboarding.engineTitle) {
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
            text = if (running) strings.onboarding.engineInitializing else strings.onboarding.engineReady,
            color = Gp.Ink,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = strings.onboarding.engineSubtitle,
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
        if (!containerAvailable) {
            InfoBanner(
                title = strings.onboarding.containerNotReadyTitle,
                text = strings.onboarding.containerNotReadyText,
                tint = Gp.Orange50,
                border = Color(0xFFFFE4C2),
                textColor = Color(0xFFB54708),
                modifier = Modifier.padding(bottom = 12.dp),
            )
            GpButton(
                text = strings.onboarding.checkContainer,
                onClick = onContainerInit,
                style = GpButtonStyle.Outline,
                height = 42.dp,
            )
            Spacer(Modifier.height(12.dp))
        }

        if (engineError != null) {
            InfoBanner(
                title = "${ErrorCode.ENG_0102.code} · ${strings.error.title(ErrorCode.ENG_0102)}",
                text = strings.error.cause(ErrorCode.ENG_0102) + "\n" + strings.error.action(ErrorCode.ENG_0102),
                tint = Gp.Red50,
                border = Color(0xFFFFD5D2),
                textColor = Color(0xFFB42318),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        if (capability.supported) {
            GpButton(
                text = strings.onboarding.enterHome,
                onClick = onDone,
                enabled = !running,
            )
        } else {
            GpButton(text = strings.onboarding.viewUnsupportedReason, onClick = onUnsupported)
        }
        Spacer(Modifier.height(8.dp))
        GpButton(
            text = strings.onboarding.recheck,
            onClick = { vm.runSelfCheck() },
            style = GpButtonStyle.Ghost,
            enabled = !running,
            height = 44.dp,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun StepBadge(index: Int, state: StepState) {
    val background = when (state) {
        StepState.DONE -> Gp.Green50
        StepState.WARN -> Gp.Orange50
        StepState.RUNNING -> Gp.Blue50
        StepState.PENDING -> Color(0xFFF3F5F9)
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(50))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            StepState.DONE -> Icon(GpIcons.Check, contentDescription = null, tint = Gp.Green, modifier = Modifier.size(12.dp))
            StepState.WARN -> Icon(GpIcons.Warning, contentDescription = null, tint = Gp.Orange, modifier = Modifier.size(12.dp))
            StepState.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 2.dp,
                color = Gp.Blue,
            )
            StepState.PENDING -> Text(
                text = "${index + 1}",
                color = Gp.Ink3,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
