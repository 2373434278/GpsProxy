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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.domain.simulation.route.RouteHistoryPolicy
import com.gpsproxy.dev.domain.simulation.route.SavedRoute
import com.gpsproxy.dev.domain.simulation.route.endpointSummary
import com.gpsproxy.dev.ui.components.EmptyState
import com.gpsproxy.dev.ui.components.FinePrint
import com.gpsproxy.dev.ui.components.GpIconButton
import com.gpsproxy.dev.ui.components.GpPage
import com.gpsproxy.dev.ui.components.GpTextField
import com.gpsproxy.dev.ui.components.SectionLabel
import com.gpsproxy.dev.ui.components.SurfaceCard
import com.gpsproxy.dev.ui.feature.route.RoutesViewModel
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp

@Composable
fun RoutesScreen(
    vm: RoutesViewModel,
    onBack: () -> Unit,
    onLoaded: () -> Unit,
) {
    val routes by vm.routes.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<SavedRoute?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedRoute?>(null) }

    val favorites = routes.filter { it.favorite }
    val recents = routes.filterNot { it.favorite }
    val strings = LocalStrings.current

    GpPage(title = strings.route.libraryTitle, onBack = onBack) {
        if (routes.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            EmptyState(
                icon = GpIcons.Route,
                title = strings.route.emptyTitle,
                message = strings.route.emptyMessage,
            )
        } else {
            if (favorites.isNotEmpty()) {
                SectionLabel(strings.route.favoritesSection(favorites.size))
                favorites.forEach { route ->
                    RouteCard(
                        route = route,
                        onLoad = {
                            vm.load(route)
                            onLoaded()
                        },
                        onFavorite = { vm.toggleFavorite(route) },
                        onRename = { renameTarget = route },
                        onDelete = { deleteTarget = route },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (recents.isNotEmpty()) {
                SectionLabel(strings.route.recentsSection(recents.size))
                recents.forEach { route ->
                    RouteCard(
                        route = route,
                        onLoad = {
                            vm.load(route)
                            onLoaded()
                        },
                        onFavorite = { vm.toggleFavorite(route) },
                        onRename = { renameTarget = route },
                        onDelete = { deleteTarget = route },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            FinePrint(
                strings.route.libraryHint(RouteHistoryPolicy.MAX_RECENT, RouteHistoryPolicy.MAX_FAVORITES),
                alignCenter = false,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    renameTarget?.let { target ->
        RenameRouteDialog(
            initial = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                vm.rename(target, name)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(strings.route.deleteTitle) },
            text = { Text(strings.route.deleteMessage(target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.remove(target)
                        deleteTarget = null
                    },
                ) { Text(strings.route.delete) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(strings.common.cancel)
                }
            },
        )
    }
}

@Composable
private fun RouteCard(
    route: SavedRoute,
    onLoad: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    SurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onLoad),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = route.name,
                        color = Gp.Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (route.config.kind) {
                                    RouteKind.ROAM -> Gp.Blue50
                                    RouteKind.IMPORTED -> Color(0xFFF2F4F7)
                                    RouteKind.POINT_TO_POINT -> Gp.Blue50
                                },
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            strings.route.routeKindLabel(route.config.kind.name),
                            color = if (route.config.kind == RouteKind.IMPORTED) Gp.Ink2 else Gp.Blue700,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = routeSummary(route, strings),
                    color = Gp.Ink3,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            GpIconButton(
                icon = if (route.favorite) GpIcons.StarFilled else GpIcons.Star,
                contentDescription = if (route.favorite) strings.route.unfavorite else strings.route.favorite,
                onClick = onFavorite,
                size = 30.dp,
                background = Color.Transparent,
                tint = if (route.favorite) Gp.Blue else Gp.Ink3,
            )
            GpIconButton(
                icon = GpIcons.Edit,
                contentDescription = strings.route.rename,
                onClick = onRename,
                size = 30.dp,
                background = Color.Transparent,
                tint = Gp.Ink3,
            )
            GpIconButton(
                icon = GpIcons.Trash,
                contentDescription = strings.route.delete,
                onClick = onDelete,
                size = 30.dp,
                background = Color.Transparent,
                tint = Gp.Red,
            )
        }
    }
}

private fun routeSummary(route: SavedRoute, strings: Strings): String {
    val config = route.config
    val suffix = when {
        config.loop -> strings.route.summaryLoopSuffix
        config.roundTrip -> strings.route.summaryRoundTripSuffix
        else -> ""
    }
    return when (config.kind) {
        RouteKind.ROAM -> strings.route.summaryRoam(
            config.start.formatted(),
            config.roamRadiusMeters,
            config.speedKmh,
        )
        RouteKind.IMPORTED -> strings.route.summaryImported(config.trackPoints.size, config.speedKmh)
        RouteKind.POINT_TO_POINT ->
            strings.route.summaryEndpoints(config.endpointSummary(strings), config.speedKmh)
    } + suffix
}

@Composable
private fun RenameRouteDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.route.renameTitle) },
        text = {
            Column {
                Text(strings.route.renameMessage, color = Gp.Ink2, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                GpTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = strings.route.namePlaceholder,
                    height = 44.dp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) { Text(strings.route.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.common.cancel) }
        },
    )
}
