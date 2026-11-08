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
internal fun ContainerAppRow(app: ContainerApp, vm: ContainerViewModel, busy: Boolean) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ContainerAppIcon(packageName = app.packageName, label = app.label, vm = vm)
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = Gp.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.packageName, color = Gp.Ink3, fontSize = 10.5.sp, maxLines = 1)
                if (app.hasCellFingerprint) {
                    Spacer(Modifier.width(6.dp))
                    Text(strings.container.cellSynced, color = Gp.Green, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // OWN 不放进常规选项：没有专属坐标时切过去会让应用读不到位置，
                // 因此"专属"只作为下方动作的结果出现。
                LocationMode.entries.filter { it != LocationMode.OWN }.forEach { mode ->
                    GpChip(
                        text = strings.container.locationModeLabel(mode.name),
                        selected = app.locationMode == mode,
                        onClick = { vm.setContainerLocationMode(app.packageName, mode) },
                    )
                }
                if (app.locationMode == LocationMode.OWN) {
                    GpChip(text = strings.container.ownBadge, selected = true, onClick = {})
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = strings.container.useCurrentPoint,
                    color = Gp.Blue,
                    fontSize = 10.5.sp,
                    modifier = Modifier.clickable { vm.setAppLocation(app.packageName) },
                )
                if (app.locationMode == LocationMode.OWN) {
                    Text(
                        text = strings.container.resumeFollow,
                        color = Gp.Ink3,
                        fontSize = 10.5.sp,
                        modifier = Modifier.clickable { vm.clearAppLocation(app.packageName) },
                    )
                }
            }
        }
        GpIconButton(
            icon = GpIcons.Play,
            contentDescription = strings.container.launchInContainer(app.label),
            onClick = { vm.launchContainerApp(app.packageName) },
            size = 34.dp,
        )
        GpIconButton(
            icon = GpIcons.Trash,
            contentDescription = strings.container.uninstallFromContainer(app.label),
            onClick = { vm.uninstallContainerApp(app.packageName) },
            tint = Gp.Ink2,
            size = 34.dp,
        )
    }
    GpDivider()
}

@Composable
internal fun ContainerAppIcon(packageName: String, label: String, vm: ContainerViewModel) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, packageName) {
        value = vm.loadAppIcon(packageName)
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Gp.Blue50),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1),
                color = Gp.Blue700,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun ImportAppDialog(
    vm: ContainerViewModel,
    containerPackages: Set<String>,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    val hostApps by vm.installedApps.collectAsStateWithLifecycle()
    val loading by vm.appsLoading.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(hostApps, query, containerPackages) {
        hostApps.filter {
            !containerPackages.contains(it.packageName) &&
                (query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.container.importInstalled) },
        text = {
            Column {
                GpTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = strings.container.filterPlaceholder,
                    leadingIcon = GpIcons.Search,
                    height = 42.dp,
                )
                Spacer(Modifier.height(8.dp))
                if (loading && hostApps.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gp.Blue, strokeWidth = 2.5.dp)
                    }
                } else {
                    LazyColumn(Modifier.height(360.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            ImportRow(app = app, onImport = { onImport(app.packageName) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.common.close) }
        },
    )
}

@Composable
internal fun ImportRow(app: InstalledApp, onImport: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(app.label, color = Gp.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(app.packageName, color = Gp.Ink3, fontSize = 10.sp, maxLines = 1)
        }
        GpIconButton(
            icon = GpIcons.Plus,
            contentDescription = strings.container.importApp(app.label),
            onClick = onImport,
            tint = Gp.Blue,
            size = 32.dp,
        )
    }
}