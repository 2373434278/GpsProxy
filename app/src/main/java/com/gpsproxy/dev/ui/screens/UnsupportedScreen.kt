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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.ui.feature.startup.StartupViewModel
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.components.ErrorCard
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpPage
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp

@Composable
fun UnsupportedScreen(
    vm: StartupViewModel,
    onBack: () -> Unit,
) {
    val capability = vm.capability
    var showSupport by remember { mutableStateOf(false) }
    val strings = LocalStrings.current

    GpPage(title = strings.onboarding.deviceCheckTitle, onBack = onBack) {
        Spacer(Modifier.height(26.dp))
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(Gp.Red50)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GpIcons.Warning, contentDescription = null, tint = Gp.Red, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = strings.onboarding.unsupportedTitle,
            color = Gp.Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = strings.onboarding.unsupportedSubtitle,
            color = Gp.Ink3,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        val issues = capability.issues.ifEmpty {
            listOf(
                com.gpsproxy.dev.core.device.CapabilityIssue(
                    index = 1,
                    text = strings.onboarding.unsupportedFallbackIssue,
                    code = "ENG-0102",
                )
            )
        }
        issues.forEachIndexed { index, issue ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Gp.Red50),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", color = Gp.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(issue.text, color = Gp.Ink2, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        ErrorCard(
            title = strings.onboarding.errorCodeLine(ErrorCode.ENG_0102.code),
            message = strings.onboarding.unsupportedMessage + "\n\n" +
                strings.onboarding.currentEnv(capability.apiLevel, capability.abiLabel),
        )
        Spacer(Modifier.height(16.dp))
        GpButton(
            text = strings.onboarding.viewSupportedDevices,
            onClick = { showSupport = true },
            style = GpButtonStyle.Outline,
        )
        Spacer(Modifier.height(8.dp))
        GpButton(text = strings.common.back, onClick = onBack, style = GpButtonStyle.Ghost, height = 44.dp)
    }

    if (showSupport) {
        AlertDialog(
            onDismissRequest = { showSupport = false },
            title = { Text(strings.onboarding.supportScopeTitle) },
            text = {
                Text(
                    strings.onboarding.supportScopeText
                )
            },
            confirmButton = {
                TextButton(onClick = { showSupport = false }) {
                    Text(strings.onboarding.gotIt)
                }
            },
        )
    }
}
