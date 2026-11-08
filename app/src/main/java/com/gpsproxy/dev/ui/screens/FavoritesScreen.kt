package com.gpsproxy.dev.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.domain.place.FavoritePlace
import com.gpsproxy.dev.domain.place.HistoryEntry
import com.gpsproxy.dev.ui.feature.favorites.FavoritesViewModel
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.components.GpDivider
import com.gpsproxy.dev.ui.components.EmptyState
import com.gpsproxy.dev.ui.components.GpAppBar
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpIconButton
import com.gpsproxy.dev.ui.components.SurfaceCard
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp
import kotlinx.coroutines.delay

@Composable
fun FavoritesScreen(
    vm: FavoritesViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onGoHome: () -> Unit,
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val center by vm.mapCenter.collectAsStateWithLifecycle()
    var showFavorites by remember { mutableStateOf(true) }
    var renameTarget by remember { mutableStateOf<FavoritePlace?>(null) }
    var pendingUndo by remember { mutableStateOf<FavoritePlace?>(null) }

    LaunchedEffect(pendingUndo?.id) {
        if (pendingUndo != null) {
            delay(3000)
            pendingUndo = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        GpAppBar(
            title = strings.place.favoritesTitle,
            onBack = onBack,
            actions = {
                GpIconButton(
                    icon = GpIcons.Plus,
                    contentDescription = strings.place.addNew,
                    onClick = onAdd,
                )
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            listOf(strings.place.favoritesTab, strings.place.historyTab).forEachIndexed { index, label ->
                val selected = showFavorites == (index == 0)
                Column(
                    modifier = Modifier.clickable { showFavorites = index == 0 },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        color = if (selected) Gp.Ink else Gp.Ink2,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Box(
                        Modifier
                            .width(26.dp)
                            .height(2.dp)
                            .background(if (selected) Gp.Blue else Color.Transparent, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
        GpDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showFavorites) {
                if (favorites.isEmpty()) {
                    EmptyState(
                        icon = GpIcons.Star,
                        title = strings.place.emptyFavoritesTitle,
                        message = strings.place.emptyFavoritesMessage,
                        action = {
                            GpButton(
                                text = strings.place.mapPick,
                                onClick = onAdd,
                                style = GpButtonStyle.Outline,
                                height = 40.dp,
                            )
                        },
                    )
                } else {
                    LazyColumn {
                        items(favorites, key = { it.id }) { favorite ->
                            SwipeFavoriteRow(
                                favorite = favorite,
                                distance = GeoUtils.distanceMeters(
                                    center,
                                    GeoPoint(favorite.lat, favorite.lng),
                                ),
                                onClick = {
                                    vm.applyFavorite(favorite)
                                    onGoHome()
                                },
                                onRename = { renameTarget = favorite },
                                onDelete = {
                                    vm.removeFavorite(favorite.id)
                                    pendingUndo = favorite
                                },
                            )
                        }
                        item { Spacer(Modifier.height(10.dp)) }
                    }
                }
            } else {
                if (history.isEmpty()) {
                    EmptyState(
                        icon = GpIcons.Clock,
                        title = strings.place.noHistoryTitle,
                        message = strings.place.noHistoryMessage,
                    )
                } else {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                strings.place.recentCount(history.size),
                                color = Gp.Ink3,
                                fontSize = 11.5.sp,
                            )
                            Text(
                                strings.place.clear,
                                color = Gp.Ink3,
                                fontSize = 11.5.sp,
                                modifier = Modifier.clickable(onClick = vm::clearHistory),
                            )
                        }
                        LazyColumn {
                            items(history, key = { it.id }) { entry ->
                                HistoryRow(
                                    entry = entry,
                                    distance = GeoUtils.distanceMeters(
                                        center,
                                        GeoPoint(entry.lat, entry.lng),
                                    ),
                                    onClick = {
                                        vm.applyHistory(entry)
                                        onGoHome()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingUndo != null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = Color(0xF21B212B),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text(
                        strings.place.deletedUndo(pendingUndo?.name.orEmpty()),
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                    Text(
                        strings.place.undo,
                        color = Color(0xFF7FC4FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            pendingUndo?.let { vm.restoreFavorite(it) }
                            pendingUndo = null
                        },
                    )
                }
            }
        }
    }

    val target = renameTarget
    if (target != null) {
        var value by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(strings.place.renameFavorite) },
            text = {
                com.gpsproxy.dev.ui.components.GpTextField(
                    value = value,
                    onValueChange = { value = it.take(20) },
                    placeholder = strings.place.renameFavoriteHint,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameFavorite(target.id, value)
                    renameTarget = null
                }) { Text(strings.route.save) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(strings.common.cancel)
                }
            },
        )
    }
}
