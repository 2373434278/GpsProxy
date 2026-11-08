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


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SwipeFavoriteRow(
    favorite: FavoritePlace,
    distance: Double,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Gp.Red50)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(GpIcons.Trash, contentDescription = null, tint = Gp.Red, modifier = Modifier.size(20.dp))
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .combinedClickable(onClick = onClick, onLongClick = onRename)
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFFF3F7FF)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GpIcons.Star, contentDescription = null, tint = Gp.Blue, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(favorite.name, color = Gp.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${String.format(java.util.Locale.US, "%.6f, %.6f", favorite.lat, favorite.lng)} · ${favorite.coordSystem.label}",
                    color = Gp.Ink3,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            Text(
                GeoUtils.formatDistance(distance),
                color = Gp.Blue700,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        GpDivider()
    }
}

@Composable
internal fun HistoryRow(entry: HistoryEntry, distance: Double, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFFF4F6FA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GpIcons.Clock, contentDescription = null, tint = Gp.Ink3, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(entry.name, color = Gp.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(entry.address, color = Gp.Ink3, fontSize = 11.sp, maxLines = 1)
        }
        Text(GeoUtils.formatDistance(distance), color = Gp.Ink3, fontSize = 11.sp)
    }
    GpDivider()
}
