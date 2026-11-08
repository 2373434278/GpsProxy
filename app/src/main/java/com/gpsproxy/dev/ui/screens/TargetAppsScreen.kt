package com.gpsproxy.dev.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.gpsproxy.dev.data.InstalledApp
import com.gpsproxy.dev.ui.feature.apps.TargetAppsViewModel
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.components.GpDivider
import com.gpsproxy.dev.ui.components.EmptyState
import com.gpsproxy.dev.ui.components.GpAppBar
import com.gpsproxy.dev.ui.components.GpIconButton
import com.gpsproxy.dev.ui.components.GpTextField
import com.gpsproxy.dev.ui.components.InfoBanner
import com.gpsproxy.dev.ui.components.SectionLabel
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp

@Composable
fun TargetAppsScreen(
    vm: TargetAppsViewModel,
    onBack: () -> Unit,
) {
    val apps by vm.installedApps.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.ensureAppsLoaded() }

    val filtered = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
    val selectedApps = apps.filter { settings.targetPackages.contains(it.packageName) }
    val strings = LocalStrings.current
    val selectedSet = settings.targetPackages

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        GpAppBar(
            title = strings.container.targetAppsTitle,
            onBack = onBack,
            actions = {
                GpIconButton(
                    icon = GpIcons.Refresh,
                    contentDescription = strings.container.refresh,
                    onClick = { vm.ensureAppsLoaded(force = true) },
                )
            },
        )

        InfoBanner(
            title = strings.container.commonAppsTitle,
            text = strings.container.commonAppsBanner,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        GpTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = strings.container.filterPlaceholder,
            leadingIcon = GpIcons.Search,
            height = 42.dp,
        )
        Spacer(Modifier.height(6.dp))

        Box(Modifier.weight(1f)) {
            when {
                loading && apps.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gp.Blue, strokeWidth = 2.5.dp)
                    }
                }
                apps.isEmpty() -> {
                    EmptyState(
                        icon = GpIcons.Grid,
                        title = strings.container.noLaunchableAppsTitle,
                        message = strings.container.noLaunchableAppsMessage,
                    )
                }
                else -> {
                    LazyColumn {
                        if (selectedApps.isNotEmpty()) {
                            item { SectionLabel(strings.container.sectionCommon) }
                            items(selectedApps, key = { "selected-${it.packageName}" }) { app ->
                                AppRow(
                                    app = app,
                                    selected = true,
                                    vm = vm,
                                    onToggle = { vm.toggleTargetPackage(app.packageName) },
                                )
                            }
                            item { SectionLabel(strings.container.sectionAllApps(filtered.size)) }
                        } else {
                            item { SectionLabel(strings.container.sectionAllApps(filtered.size)) }
                        }
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                selected = selectedSet.contains(app.packageName),
                                vm = vm,
                                onToggle = { vm.toggleTargetPackage(app.packageName) },
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    selected: Boolean,
    vm: TargetAppsViewModel,
    onToggle: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(packageName = app.packageName, label = app.label, vm = vm)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = app.label,
                    color = Gp.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (app.isSystem) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFF3F5F9))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(strings.container.systemBadge, color = Gp.Ink3, fontSize = 9.sp)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(app.packageName, color = Gp.Ink3, fontSize = 10.5.sp, maxLines = 1)
        }
        GpIconButton(
            icon = GpIcons.Play,
            contentDescription = strings.container.openApp(app.label),
            onClick = { vm.launchApp(app.packageName) },
            size = 34.dp,
        )
        GpIconButton(
            icon = if (selected) GpIcons.StarFilled else GpIcons.Star,
            contentDescription = if (selected) strings.container.removeCommon else strings.container.addCommon,
            onClick = onToggle,
            tint = if (selected) Gp.Blue else Gp.Ink2,
            size = 34.dp,
        )
    }
    GpDivider()
}

@Composable
private fun AppIcon(packageName: String, label: String, vm: TargetAppsViewModel) {
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
