package com.gpsproxy.dev.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsproxy.dev.domain.container.ContainerApp
import com.gpsproxy.dev.domain.container.LocationMode
import com.gpsproxy.dev.data.InstalledApp
import com.gpsproxy.dev.ui.feature.container.ContainerViewModel
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.components.EmptyState
import com.gpsproxy.dev.ui.components.GpAppBar
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpChip
import com.gpsproxy.dev.ui.components.GpDivider
import com.gpsproxy.dev.ui.components.GpIconButton
import com.gpsproxy.dev.ui.components.GpTextField
import com.gpsproxy.dev.ui.components.InfoBanner
import com.gpsproxy.dev.ui.components.SectionLabel
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp

@Composable
fun ContainerAppsScreen(
    vm: ContainerViewModel,
    onBack: () -> Unit,
    onInit: () -> Unit,
) {
    val apps by vm.containerApps.collectAsStateWithLifecycle()
    val appsUnavailable by vm.containerAppsUnavailable.collectAsStateWithLifecycle()
    val loading by vm.containerLoading.collectAsStateWithLifecycle()
    val busy by vm.containerBusy.collectAsStateWithLifecycle()
    val available by vm.available.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val sandboxBytes by vm.sandboxBytes.collectAsStateWithLifecycle()
    var showImportDialog by remember { mutableStateOf(false) }

    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importContainerApk(it) } }

    LaunchedEffect(Unit) { vm.ensureContainerAppsLoaded() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        GpAppBar(
            title = strings.container.containerAppsTitle,
            onBack = onBack,
            actions = {
                GpIconButton(
                    icon = GpIcons.Refresh,
                    contentDescription = strings.container.refresh,
                    onClick = { vm.ensureContainerAppsLoaded() },
                )
            },
        )

        if (!available) {
            InfoBanner(
                title = strings.container.engineUnavailableTitle,
                text = strings.container.engineUnavailableText,
                tint = Gp.Orange50,
                border = Color(0xFFFFE4C2),
                textColor = Color(0xFFB54708),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            GpButton(
                text = strings.container.goEngineCheck,
                onClick = onInit,
                style = GpButtonStyle.Outline,
                height = 42.dp,
            )
            Spacer(Modifier.height(8.dp))
        } else {
            InfoBanner(
                title = strings.container.readsMockTitle,
                text = strings.container.readsMockText,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = strings.container.sandboxUsage("%.1f".format(sandboxBytes / 1048576.0)),
                color = Gp.Ink3,
                fontSize = 11.sp,
            )
            if (busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Gp.Blue)
                    Text(strings.container.processing, color = Gp.Ink3, fontSize = 11.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GpButton(
                text = strings.container.importInstalled,
                onClick = {
                    vm.ensureAppsLoaded()
                    showImportDialog = true
                },
                enabled = available && !busy,
                style = GpButtonStyle.Outline,
                height = 42.dp,
                modifier = Modifier.weight(1f),
            )
            GpButton(
                text = strings.container.importApk,
                onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) },
                enabled = available && !busy,
                style = GpButtonStyle.Outline,
                height = 42.dp,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        GpButton(
            text = strings.container.restoreAll,
            onClick = { vm.resetContainerLocations() },
            enabled = available && !busy,
            style = GpButtonStyle.Outline,
            height = 40.dp,
        )
        Spacer(Modifier.height(6.dp))

        Box(Modifier.weight(1f)) {
            when {
                loading && apps.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gp.Blue, strokeWidth = 2.5.dp)
                    }
                }
                appsUnavailable && apps.isEmpty() -> {
                    EmptyState(
                        icon = GpIcons.Layers,
                        title = strings.container.loadAppsFailedTitle,
                        message = strings.container.loadAppsFailedMessage,
                    )
                }
                apps.isEmpty() -> {
                    EmptyState(
                        icon = GpIcons.Layers,
                        title = strings.container.emptyContainerTitle,
                        message = strings.container.emptyContainerMessage,
                    )
                }
                else -> {
                    LazyColumn {
                        item { SectionLabel(strings.container.containerAppsSection(apps.size)) }
                        items(apps, key = { it.packageName }) { app ->
                            ContainerAppRow(app = app, vm = vm, busy = busy)
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        ImportAppDialog(
            vm = vm,
            containerPackages = apps.map { it.packageName }.toSet(),
            onDismiss = { showImportDialog = false },
            onImport = {
                vm.importContainerApp(it)
                showImportDialog = false
            },
        )
    }
}
