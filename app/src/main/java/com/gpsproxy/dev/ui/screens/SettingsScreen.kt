package com.gpsproxy.dev.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.i18n.AppLanguage
import com.gpsproxy.dev.domain.settings.DetailLevel
import com.gpsproxy.dev.domain.settings.InjectionChannel
import com.gpsproxy.dev.ui.feature.settings.SettingsViewModel
import com.gpsproxy.dev.domain.settings.NotificationDetail
import com.gpsproxy.dev.ui.components.FinePrint
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpChip
import com.gpsproxy.dev.ui.components.GpDivider
import com.gpsproxy.dev.ui.components.GpOptionDialog
import com.gpsproxy.dev.ui.components.GpPage
import com.gpsproxy.dev.ui.components.GpSwitch
import com.gpsproxy.dev.ui.components.InfoBanner
import com.gpsproxy.dev.ui.components.SectionLabel
import com.gpsproxy.dev.ui.components.SettingRow
import com.gpsproxy.dev.ui.components.ValuePill
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.theme.Gp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onDiagnostic: () -> Unit,
    onContainer: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val containerAvailable by vm.containerAvailable.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    var showAccuracy by remember { mutableStateOf(false) }
    var showInterval by remember { mutableStateOf(false) }
    var showLogLevel by remember { mutableStateOf(false) }
    var showBlurRadius by remember { mutableStateOf(false) }
    var showReportSpeed by remember { mutableStateOf(false) }
    var showReportBearing by remember { mutableStateOf(false) }
    var showAltitude by remember { mutableStateOf(false) }
    var showWalkSpeed by remember { mutableStateOf(false) }
    var showAutoStop by remember { mutableStateOf(false) }

    GpPage(title = strings.settings.pageTitle, onBack = onBack) {
        SectionLabel(strings.settings.coordSystemSection)
        SettingRow(
            title = strings.settings.coordSystemTitle,
            subtitle = strings.settings.coordSystemSubtitle,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            CoordSystem.entries.forEach { system ->
                GpChip(
                    text = system.label,
                    selected = settings.coordSystem == system,
                    onClick = { vm.setCoordSystem(system) },
                )
            }
        }
        FinePrint(strings.settings.coordSystemDescription(settings.coordSystem.name), alignCenter = false)

        SectionLabel(strings.settings.locationSection)
        SettingRow(
            title = strings.settings.accuracyTitle,
            subtitle = strings.settings.accuracySubtitle,
            trailing = {
                ValuePill(
                    strings.settings.accuracyOption(settings.accuracyMeters),
                    onClick = { showAccuracy = true },
                )
            },
        )
        GpDivider()
        SettingRow(
            title = strings.settings.driftTitle,
            subtitle = strings.settings.driftSubtitle,
            trailing = { GpSwitch(checked = settings.driftEnabled, onCheckedChange = { vm.setDrift(it) }) },
        )
        GpDivider()
        SettingRow(
            title = strings.settings.blurTitle,
            subtitle = strings.settings.blurSubtitle,
            trailing = { GpSwitch(checked = settings.blurEnabled, onCheckedChange = { vm.setBlurEnabled(it) }) },
        )
        if (settings.blurEnabled) {
            GpDivider()
            SettingRow(
                title = strings.settings.blurRadiusTitle,
                subtitle = strings.settings.blurRadiusSubtitle,
                trailing = {
                    ValuePill(
                        strings.settings.accuracyOption(settings.blurRadiusMeters),
                        onClick = { showBlurRadius = true },
                    )
                },
            )
        }
        GpDivider()
        SettingRow(
            title = strings.settings.intervalTitle,
            subtitle = strings.settings.intervalSubtitle,
            trailing = {
                ValuePill(
                    strings.settings.intervalOption(settings.updateIntervalMs),
                    onClick = { showInterval = true },
                )
            },
        )

        SectionLabel(strings.settings.payloadSection)
        SettingRow(
            title = strings.settings.reportSpeedTitle,
            subtitle = strings.settings.reportSpeedSubtitle,
            trailing = {
                ValuePill(
                    strings.settings.kmh(settings.reportSpeedKmh),
                    onClick = { showReportSpeed = true },
                )
            },
        )
        GpDivider()
        SettingRow(
            title = strings.settings.reportBearingTitle,
            subtitle = strings.settings.reportBearingSubtitle,
            trailing = {
                ValuePill(
                    strings.settings.degrees(settings.reportBearingDeg),
                    onClick = { showReportBearing = true },
                )
            },
        )
        GpDivider()
        SettingRow(
            title = strings.settings.altitudeTitle,
            subtitle = strings.settings.altitudeSubtitle,
            trailing = {
                ValuePill(
                    strings.settings.meters(settings.altitudeMeters),
                    onClick = { showAltitude = true },
                )
            },
        )
        GpDivider()
        SettingRow(
            title = strings.settings.walkSpeedTitle,
            subtitle = strings.settings.walkSpeedSubtitle,
            trailing = {
                ValuePill(
                    strings.settings.kmh(settings.walkSpeedKmh),
                    onClick = { showWalkSpeed = true },
                )
            },
        )

        SectionLabel(strings.settings.sessionSection)
        SettingRow(
            title = strings.settings.autoStopTitle,
            subtitle = strings.settings.autoStopSubtitle,
            trailing = {
                ValuePill(
                    if (settings.autoStopMinutes == 0) {
                        strings.settings.unlimited
                    } else {
                        strings.settings.minutes(settings.autoStopMinutes)
                    },
                    onClick = { showAutoStop = true },
                )
            },
        )

        SectionLabel(strings.settings.injectionSection)
        SettingRow(
            title = strings.settings.channelTitle,
            subtitle = strings.settings.channelSubtitle,
            trailing = {
                ValuePill(strings.settings.channelName(settings.injectionChannel.name))
            },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            InjectionChannel.entries.forEach { channel ->
                GpChip(
                    text = strings.settings.channelName(channel.name),
                    selected = settings.injectionChannel == channel,
                    onClick = { vm.setInjectionChannel(channel) },
                )
            }
        }
        FinePrint(strings.settings.channelDescription(settings.injectionChannel.name), alignCenter = false)
        GpDivider()
        SettingRow(
            title = strings.settings.containerRuntimeTitle,
            subtitle = if (containerAvailable) {
                strings.settings.containerRuntimeReady
            } else {
                strings.settings.containerRuntimeUnavailable
            },
            trailing = {
                if (containerAvailable) {
                    ValuePill(strings.settings.ready)
                } else {
                    ValuePill(strings.settings.unavailable, enabled = false)
                }
            },
        )
        GpDivider()
        SettingRow(
            title = strings.settings.containerSpaceTitle,
            subtitle = strings.settings.containerSpaceSubtitle,
            trailing = {
                Icon(GpIcons.ChevronRight, contentDescription = null, tint = Gp.Ink3)
            },
            onClick = onContainer,
        )
        GpDivider()
        SettingRow(
            title = strings.settings.overlayTitle,
            subtitle = strings.settings.overlaySubtitle,
            trailing = { GpSwitch(checked = settings.overlayEnabled, onCheckedChange = { vm.setOverlayEnabled(it) }) },
        )

        SectionLabel(strings.settings.generalSection)
        SettingRow(
            title = strings.settings.languageTitle,
            subtitle = strings.settings.languageSubtitle,
            trailing = { },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            AppLanguage.entries.forEach { language ->
                GpChip(
                    text = strings.settings.languageName(language),
                    selected = settings.language == language,
                    onClick = { vm.setLanguage(language) },
                )
            }
        }
        GpDivider()

        SectionLabel(strings.settings.startupLogSection)
        SettingRow(
            title = strings.settings.lastSelfCheckTitle,
            subtitle = if (settings.lastSelfCheckAt == 0L) {
                strings.settings.lastSelfCheckNever
            } else {
                strings.settings.lastSelfCheckAt(
                    SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(settings.lastSelfCheckAt)),
                )
            },
            trailing = {
                ValuePill(
                    if (settings.lastSelfCheckAt == 0L) strings.settings.never else strings.settings.checked,
                )
            },
        )
        GpDivider()
        SettingRow(
            title = strings.settings.notificationDetailTitle,
            subtitle = strings.settings.notificationDetailSubtitle,
            trailing = { },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            NotificationDetail.entries.forEach { detail ->
                GpChip(
                    text = strings.settings.notificationDetailName(detail.name),
                    selected = settings.notificationDetail == detail,
                    onClick = { vm.setNotificationDetail(detail) },
                )
            }
        }
        GpDivider()
        SettingRow(
            title = strings.settings.bootRestoreTitle,
            subtitle = strings.settings.bootRestoreSubtitle,
            trailing = { GpSwitch(checked = settings.bootRestore, onCheckedChange = { vm.setBootRestore(it) }) },
        )
        GpDivider()
        SettingRow(
            title = strings.settings.logLevelTitle,
            subtitle = strings.settings.logLevelSubtitle,
            trailing = {
                ValuePill(
                    strings.settings.detailLevelName(settings.detailLevel.name),
                    onClick = { showLogLevel = true },
                )
            },
        )
        GpDivider()
        SettingRow(
            title = strings.settings.clearLogsTitle,
            subtitle = strings.settings.clearLogsSubtitle,
            onClick = { vm.clearLogs() },
        )

        Spacer(Modifier.height(16.dp))
        InfoBanner(
            title = strings.settings.capabilityTitle,
            text = strings.settings.capabilityText,
            tint = Gp.Orange50,
            border = Color(0xFFFFE4C2),
            textColor = Color(0xFFB54708),
        )

        Spacer(Modifier.height(14.dp))
        GpButton(
            text = strings.settings.openDiagnostics,
            onClick = onDiagnostic,
            style = GpButtonStyle.Outline,
        )

        SectionLabel(strings.settings.aboutSection)
        SettingRow(
            title = strings.settings.versionTitle,
            subtitle = strings.settings.versionSubtitle(vm.appVersion, vm.buildType),
            trailing = { ValuePill(vm.appVersion) },
        )
        GpDivider()
        SettingRow(
            title = strings.settings.poiSourceTitle,
            subtitle = if (vm.poiLiveSource) {
                strings.settings.poiSourceLive
            } else {
                strings.settings.poiSourceBuiltin
            },
            trailing = {
                ValuePill(if (vm.poiLiveSource) strings.settings.amap else strings.settings.builtin)
            },
        )
        GpDivider()
        SettingRow(
            title = strings.settings.supportTitle,
            subtitle = strings.settings.supportSubtitle,
            trailing = { ValuePill(strings.settings.supportValue) },
        )
        FinePrint(strings.settings.privacy)
        Spacer(Modifier.height(12.dp))
    }

    if (showAccuracy) {
        GpOptionDialog(
            title = strings.settings.accuracyDialogTitle,
            options = listOf(3, 5, 10, 20, 50).map { strings.settings.accuracyOption(it) to it },
            selected = settings.accuracyMeters,
            onDismiss = { showAccuracy = false },
            onSelect = {
                vm.setAccuracy(it)
                showAccuracy = false
            },
        )
    }
    if (showInterval) {
        GpOptionDialog(
            title = strings.settings.intervalDialogTitle,
            options = listOf(500, 1000, 2000).map { strings.settings.intervalOption(it) to it },
            selected = settings.updateIntervalMs,
            onDismiss = { showInterval = false },
            onSelect = {
                vm.setUpdateInterval(it)
                showInterval = false
            },
        )
    }
    if (showBlurRadius) {
        GpOptionDialog(
            title = strings.settings.blurRadiusDialogTitle,
            options = listOf(50, 100, 200, 500, 1000, 2000).map { strings.settings.accuracyOption(it) to it },
            selected = settings.blurRadiusMeters,
            onDismiss = { showBlurRadius = false },
            onSelect = {
                vm.setBlurRadius(it)
                showBlurRadius = false
            },
        )
    }
    if (showLogLevel) {
        GpOptionDialog(
            title = strings.settings.logLevelDialogTitle,
            options = listOf(
                strings.settings.detailLevelName(DetailLevel.NORMAL.name) to DetailLevel.NORMAL,
                strings.settings.detailLevelName(DetailLevel.VERBOSE.name) to DetailLevel.VERBOSE,
            ),
            selected = settings.detailLevel,
            onDismiss = { showLogLevel = false },
            onSelect = {
                vm.setDetailLevel(it)
                showLogLevel = false
            },
        )
    }
    if (showReportSpeed) {
        GpOptionDialog(
            title = strings.settings.reportSpeedDialogTitle,
            options = listOf(0, 5, 15, 30, 50, 80, 120).map { strings.settings.kmh(it) to it },
            selected = settings.reportSpeedKmh,
            onDismiss = { showReportSpeed = false },
            onSelect = {
                vm.setReportSpeed(it)
                showReportSpeed = false
            },
        )
    }
    if (showReportBearing) {
        GpOptionDialog(
            title = strings.settings.reportBearingDialogTitle,
            options = listOf(0, 45, 90, 135, 180, 225, 270, 315).map { strings.settings.degrees(it) to it },
            selected = settings.reportBearingDeg,
            onDismiss = { showReportBearing = false },
            onSelect = {
                vm.setReportBearing(it)
                showReportBearing = false
            },
        )
    }
    if (showAltitude) {
        GpOptionDialog(
            title = strings.settings.altitudeDialogTitle,
            options = listOf(0, 12, 50, 100, 200, 500, 1000, 2000).map { strings.settings.meters(it) to it },
            selected = settings.altitudeMeters,
            onDismiss = { showAltitude = false },
            onSelect = {
                vm.setAltitude(it)
                showAltitude = false
            },
        )
    }
    if (showWalkSpeed) {
        GpOptionDialog(
            title = strings.settings.walkSpeedDialogTitle,
            options = listOf(2, 3, 5, 8, 10, 15, 20).map { strings.settings.kmh(it) to it },
            selected = settings.walkSpeedKmh,
            onDismiss = { showWalkSpeed = false },
            onSelect = {
                vm.setWalkSpeed(it)
                showWalkSpeed = false
            },
        )
    }
    if (showAutoStop) {
        GpOptionDialog(
            title = strings.settings.autoStopDialogTitle,
            options = listOf(
                strings.settings.unlimited to 0,
                strings.settings.minutes(5) to 5,
                strings.settings.minutes(10) to 10,
                strings.settings.minutes(15) to 15,
                strings.settings.minutes(30) to 30,
                strings.settings.minutes(60) to 60,
            ),
            selected = settings.autoStopMinutes,
            onDismiss = { showAutoStop = false },
            onSelect = {
                vm.setAutoStop(it)
                showAutoStop = false
            },
        )
    }
}
