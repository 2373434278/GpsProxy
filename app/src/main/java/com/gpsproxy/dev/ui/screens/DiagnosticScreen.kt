package com.gpsproxy.dev.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsproxy.dev.core.log.LogSeverity
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.domain.simulation.model.ChannelStatus
import com.gpsproxy.dev.ui.feature.diagnostic.DiagnosticsViewModel
import com.gpsproxy.dev.ui.components.GpDivider
import com.gpsproxy.dev.ui.components.ErrorCard
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpIconButton
import com.gpsproxy.dev.ui.components.GpPage
import com.gpsproxy.dev.ui.components.InfoBanner
import com.gpsproxy.dev.ui.components.SectionLabel
import com.gpsproxy.dev.ui.components.StatusDot
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp

@Composable
fun DiagnosticScreen(
    vm: DiagnosticsViewModel,
    onBack: () -> Unit,
) {
    val channels by vm.channels.collectAsStateWithLifecycle()
    val simulation by vm.simulation.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    var confirmExport by remember { mutableStateOf(false) }
    val error = simulation.errorCode
    val strings = LocalStrings.current

    GpPage(
        title = strings.channel.pageTitle,
        onBack = onBack,
        actions = {
            GpIconButton(
                icon = GpIcons.Upload,
                contentDescription = strings.channel.exportContentDescription,
                onClick = { confirmExport = true },
            )
        },
    ) {
        InfoBanner(
            text = strings.channel.infoBanner,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )

        SectionLabel(strings.channel.channelsSection)
        channels.forEach { channel ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusDot(status = channel.status, modifier = Modifier.padding(top = 1.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.channel.channelName(channel.id.name),
                        color = Gp.Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = channel.note
                            ?.let { strings.channel.note(it.kind.name, it.count) }
                            ?: strings.channel.channelDescription(channel.id.name),
                        color = Gp.Ink3,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                Text(
                    text = when (channel.status) {
                        ChannelStatus.OK -> strings.channel.hitCount(channel.count)
                        ChannelStatus.WARN -> strings.channel.statusMiss
                        ChannelStatus.BAD -> strings.channel.statusUnavailable
                        ChannelStatus.OFF -> if (channel.count > 0) {
                            strings.channel.hitCount(channel.count)
                        } else {
                            strings.channel.statusDisabled
                        }
                    },
                    color = when (channel.status) {
                        ChannelStatus.OK -> Gp.Green
                        ChannelStatus.WARN -> Gp.Orange
                        ChannelStatus.BAD -> Gp.Red
                        ChannelStatus.OFF -> Gp.Ink3
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            GpDivider()
        }

        SectionLabel(strings.channel.mockFlagSection)
        InfoBanner(
            title = when (settings.injectionChannel) {
                InjectionChannel.CONTAINER -> strings.channel.mockFlagContainerTitle
                InjectionChannel.OFFICIAL -> strings.channel.mockFlagOfficialTitle
            },
            text = when (settings.injectionChannel) {
                InjectionChannel.CONTAINER -> strings.channel.mockFlagContainerText
                InjectionChannel.OFFICIAL -> strings.channel.mockFlagOfficialText
            },
            tint = Gp.Orange50,
            border = Color(0xFFFFE4C2),
            textColor = Color(0xFFB54708),
            modifier = Modifier.padding(bottom = 4.dp),
        )

        SectionLabel(strings.channel.blurSection)
        InfoBanner(
            title = if (simulation.blurRadiusMeters > 0) {
                strings.channel.blurOn(simulation.blurRadiusMeters)
            } else {
                strings.channel.blurOff
            },
            text = if (simulation.blurRadiusMeters > 0) {
                val selected = simulation.point?.formatted() ?: "-"
                val injected = simulation.injectedPoint?.formatted() ?: selected
                strings.channel.blurActiveText(injected, selected, simulation.blurRadiusMeters)
            } else {
                strings.channel.blurInactiveText
            },
            tint = Gp.Blue50,
            border = Gp.Blue100,
            textColor = Gp.Blue700,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        SectionLabel(strings.channel.errorsSection)
        if (error != null) {
            ErrorCard(
                title = "${error.code} · ${strings.error.title(error)}",
                message = "${strings.error.cause(error)}\n" +
                    "${strings.error.suggestionPrefix}${strings.error.action(error)}",
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Gp.Green50)
                    .padding(12.dp)
            ) {
                Text(
                    strings.channel.errorNone,
                    color = Color(0xFF067647),
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        SectionLabel(strings.channel.logsSection(logs.size))
        SurfaceLogBox(logs = logs)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = strings.channel.clearLogs,
                color = Gp.Ink3,
                fontSize = 11.5.sp,
                modifier = Modifier.clickable { vm.clearLogs() },
            )
        }

        Spacer(Modifier.height(16.dp))
        GpButton(
            text = strings.channel.export,
            onClick = { confirmExport = true },
            style = GpButtonStyle.Outline,
            icon = GpIcons.Download,
        )
        Text(
            text = strings.channel.exportHint,
            color = Gp.Ink3,
            fontSize = 10.5.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        Spacer(Modifier.height(16.dp))
    }

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text(strings.channel.export) },
            text = {
                Text(
                    strings.channel.exportDialogMessage
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmExport = false
                    vm.exportDiagnostics()
                }) { Text(strings.channel.exportConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExport = false }) {
                    Text(LocalStrings.current.common.cancel)
                }
            },
        )
    }
}

@Composable
private fun SurfaceLogBox(logs: List<com.gpsproxy.dev.core.log.LogEntry>) {
    val state = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) state.animateScrollToItem(logs.lastIndex)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
    ) {
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(LocalStrings.current.channel.noLogs, color = Color(0xFF64748B), fontSize = 11.sp)
            }
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize().padding(10.dp),
            ) {
                items(logs) { entry ->
                    Row {
                        Text(
                            text = entry.timeText(),
                            color = when (entry.severity) {
                                LogSeverity.INFO -> Color(0xFF5EE9B5)
                                LogSeverity.WARN -> Color(0xFFFFC46B)
                                LogSeverity.ERROR -> Color(0xFFFF8A8A)
                            },
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = entry.message,
                            color = Color(0xFFC3CEDF),
                            fontSize = 10.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
