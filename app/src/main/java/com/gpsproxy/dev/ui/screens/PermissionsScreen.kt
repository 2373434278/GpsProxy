package com.gpsproxy.dev.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsproxy.dev.core.device.PermissionUtils
import com.gpsproxy.dev.ui.feature.permissions.PermissionsViewModel
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.components.GpDivider
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpPage
import com.gpsproxy.dev.ui.components.InfoBanner
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp

@Composable
fun PermissionsScreen(
    vm: PermissionsViewModel,
    onBack: () -> Unit,
) {
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val context = LocalContext.current
    var showMockHelp by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        vm.refreshWithLocationMessage()
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        vm.refresh()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    GpPage(title = strings.onboarding.permissionsTitle, onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFFF2F7FF), Color.White))
                )
                .padding(16.dp),
        ) {
            Text(
                text = strings.onboarding.permissionsReady(permissions.readyCount, permissions.total),
                color = Gp.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    !permissions.mockLocation -> strings.onboarding.hintNeedMockApp
                    !permissions.locationGranted -> strings.onboarding.hintNeedLocation
                    !permissions.overlay -> strings.onboarding.hintNeedOverlay
                    else -> strings.onboarding.hintAllReady
                },
                color = Gp.Ink3,
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(6.dp))
        PermissionRow(
            icon = GpIcons.Pin,
            colors = listOf(Color(0xFF7FB8FF), Gp.Blue),
            title = strings.onboarding.permLocationTitle,
            subtitle = strings.onboarding.permLocationSubtitle,
            granted = permissions.locationGranted,
            onAction = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            },
        )
        PermissionRow(
            icon = GpIcons.Bell,
            colors = listOf(Color(0xFF8FD6B4), Gp.Green),
            title = strings.onboarding.permNotificationTitle,
            subtitle = strings.onboarding.permNotificationSubtitle,
            granted = permissions.notifications,
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionUtils.openNotificationSettings(context)
                }
            },
        )
        PermissionRow(
            icon = GpIcons.Layers,
            colors = listOf(Color(0xFFA8C8FF), Color(0xFF5B8DEF)),
            title = strings.onboarding.permOverlayTitle,
            subtitle = strings.onboarding.permOverlaySubtitle,
            granted = permissions.overlay,
            onAction = { PermissionUtils.openOverlaySettings(context) },
        )
        PermissionRow(
            icon = GpIcons.Target,
            colors = listOf(Color(0xFFFFC46B), Gp.Orange),
            title = strings.onboarding.permMockAppTitle,
            subtitle = strings.onboarding.permMockAppSubtitle,
            granted = permissions.mockLocation,
            onAction = { showMockHelp = true },
        )
        PermissionRow(
            icon = GpIcons.Battery,
            colors = listOf(Color(0xFF9ED8B8), Color(0xFF3FAE7A)),
            title = strings.onboarding.permBatteryTitle,
            subtitle = strings.onboarding.permBatterySubtitle,
            granted = permissions.batteryUnrestricted,
            onAction = { PermissionUtils.openBatterySettings(context) },
        )

        Spacer(Modifier.height(16.dp))
        InfoBanner(
            text = strings.onboarding.privacyBanner,
            tint = Color(0xFFF7F9FC),
            border = Gp.Line,
            textColor = Gp.Ink2,
        )

        Spacer(Modifier.height(12.dp))
        GpButton(
            text = strings.onboarding.refreshStatus,
            onClick = vm::refresh,
            style = GpButtonStyle.Outline,
            height = 42.dp,
        )
        Spacer(Modifier.height(16.dp))
    }

    if (showMockHelp) {
        AlertDialog(
            onDismissRequest = { showMockHelp = false },
            title = { Text(strings.onboarding.mockAppDialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    strings.onboarding.mockAppSteps.forEach {
                        Text(it, color = Gp.Ink2, fontSize = 13.sp)
                    }
                    Text(
                        strings.onboarding.mockAppFootnote,
                        color = Gp.Ink3,
                        fontSize = 11.5.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showMockHelp = false
                    PermissionUtils.openDeveloperOptions(context)
                }) { Text(strings.onboarding.goDeveloperOptions) }
            },
            dismissButton = {
                TextButton(onClick = { showMockHelp = false }) {
                    Text(strings.common.cancel)
                }
            },
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    colors: List<Color>,
    title: String,
    subtitle: String,
    granted: Boolean,
    onAction: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Gp.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Gp.Ink3, fontSize = 11.sp, lineHeight = 16.sp)
        }
        if (granted) {
            Text(strings.onboarding.granted, color = Gp.Green, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        } else {
            GpButton(
                text = strings.onboarding.goGrant,
                onClick = onAction,
                height = 34.dp,
                modifier = Modifier.width(74.dp),
            )
        }
    }
    GpDivider()
}
