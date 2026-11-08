package com.gpsproxy.dev.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpsproxy.dev.ui.feature.startup.StartupViewModel
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.components.CheckBox
import com.gpsproxy.dev.ui.components.FinePrint
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp

@Composable
fun IntroScreen(
    vm: StartupViewModel,
    onAgree: () -> Unit,
    onExit: () -> Unit,
) {
    var checked by remember { mutableStateOf(false) }
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4DA3FF), Gp.Blue)))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GpIcons.Pin, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = strings.onboarding.appTitle,
            color = Gp.Ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = strings.onboarding.tagline,
            color = Gp.Ink3,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(26.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFFBF2))
                .border(1.dp, Color(0xFFFFE7BE), RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Gp.Orange))
                Text(strings.onboarding.boundarySection, color = Color(0xFFB54708), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            listOf(
                strings.onboarding.boundaryTestOnly,
                strings.onboarding.boundaryChannels,
                strings.onboarding.boundaryScope,
                strings.onboarding.boundaryLiability,
            ).forEach { line ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("·", color = Color(0xFF7A5B2E), fontSize = 11.5.sp, modifier = Modifier.padding(end = 6.dp))
                    Text(line, color = Color(0xFF7A5B2E), fontSize = 11.5.sp, lineHeight = 19.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { checked = !checked }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CheckBox(checked = checked, onCheckedChange = { checked = it })
            Text(
                text = strings.onboarding.consentLabel,
                color = Gp.Ink2,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
        }
        Spacer(Modifier.height(22.dp))
        GpButton(
            text = strings.onboarding.agreeContinue,
            onClick = {
                vm.acceptDisclaimer()
                onAgree()
            },
            enabled = checked,
        )
        Spacer(Modifier.height(8.dp))
        GpButton(
            text = strings.onboarding.declineExit,
            onClick = onExit,
            style = GpButtonStyle.Ghost,
            height = 44.dp,
        )
        FinePrint(strings.onboarding.versionLine("0.1.0"))
        Spacer(Modifier.height(12.dp))
    }
}
