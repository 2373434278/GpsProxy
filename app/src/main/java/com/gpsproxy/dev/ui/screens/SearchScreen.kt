package com.gpsproxy.dev.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.gpsproxy.dev.domain.place.Poi
import com.gpsproxy.dev.domain.place.PoiCategory
import com.gpsproxy.dev.domain.place.PoiSource
import com.gpsproxy.dev.ui.feature.search.SearchTab
import com.gpsproxy.dev.ui.feature.search.SearchViewModel
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.components.EmptyState
import com.gpsproxy.dev.ui.components.ErrorCard
import com.gpsproxy.dev.ui.components.GpAppBar
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpChip
import com.gpsproxy.dev.ui.components.GpDivider
import com.gpsproxy.dev.ui.components.GpSegmented
import com.gpsproxy.dev.ui.components.GpTextField
import com.gpsproxy.dev.ui.components.SectionLabel
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onBack: () -> Unit,
    onSelectPoi: (Poi) -> Unit,
    onGoPick: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val center by vm.mapCenter.collectAsStateWithLifecycle()
    val searchHistory by vm.searchHistory.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        GpAppBar(title = strings.place.searchTitle, onBack = onBack)
        GpTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = strings.place.searchPlaceholder,
            leadingIcon = GpIcons.Search,
            trailing = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Gp.Blue)
                        .clickable { vm.submit() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(strings.place.searchAction, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            height = 46.dp,
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            SearchTab.entries.forEach { tab ->
                val selected = state.tab == tab
                Column(
                    modifier = Modifier.clickable { vm.setTab(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = strings.place.searchTabLabel(tab.name),
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

        if (state.tab != SearchTab.HISTORY) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PoiCategory.entries.filter { it != PoiCategory.OTHER }.forEach { category ->
                    GpChip(
                        text = strings.place.poiCategoryLabel(category.name),
                        selected = state.category == category,
                        onClick = { vm.setCategory(category) },
                    )
                }
            }
            if (state.tab == SearchTab.NEARBY) {
                GpSegmented(
                    options = listOf("500 m", "1 km", "3 km"),
                    selectedIndex = when (state.radiusMeters) {
                        500 -> 0
                        3000 -> 2
                        else -> 1
                    },
                    onSelect = { index ->
                        vm.setRadius(listOf(500, 1000, 3000)[index])
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
            if (state.source != null) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = strings.place.dataSource(
                            strings.place.poiSourceLabel(state.source?.name ?: ""),
                        ),
                        color = Gp.Ink3,
                        fontSize = 10.5.sp,
                    )
                    if (state.source == PoiSource.LOCAL) {
                        Text(
                            text = strings.place.builtinHint,
                            color = Gp.Ink3,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
            state.warning?.let {
                Text(it, color = Gp.Orange, fontSize = 10.5.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                ErrorCard(
                    title = strings.place.searchFailed,
                    message = it,
                    action = {
                        GpButton(
                            text = strings.common.retry,
                            onClick = vm::submit,
                            style = GpButtonStyle.Outline,
                            height = 38.dp,
                        )
                    },
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.tab == SearchTab.HISTORY -> {
                    if (searchHistory.isEmpty()) {
                        EmptyState(
                            icon = GpIcons.Clock,
                            title = strings.place.emptyHistoryTitle,
                            message = strings.place.emptyHistoryMessage,
                        )
                    } else {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SectionLabel(strings.place.recentSearches, Modifier.padding(top = 0.dp, bottom = 0.dp))
                                Text(
                                    strings.place.clear,
                                    color = Gp.Ink3,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.clickable { vm.clearHistory() },
                                )
                            }
                            LazyColumn {
                                items(searchHistory) { term ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                vm.setQuery(term)
                                                vm.setTab(SearchTab.KEYWORD)
                                                vm.submit()
                                            }
                                            .padding(vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            GpIcons.Clock,
                                            contentDescription = null,
                                            tint = Gp.Ink3,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(term, color = Gp.Ink2, fontSize = 13.sp)
                                    }
                                    GpDivider()
                                }
                            }
                        }
                    }
                }
                state.loading && state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gp.Blue, strokeWidth = 2.5.dp)
                    }
                }
                state.items.isEmpty() && state.error == null -> {
                    EmptyState(
                        icon = GpIcons.Search,
                        title = strings.place.noResultsTitle,
                        message = strings.place.noResultsMessage,
                        action = {
                            GpButton(
                                text = strings.place.goPick,
                                onClick = onGoPick,
                                style = GpButtonStyle.Outline,
                                height = 40.dp,
                            )
                        },
                    )
                }
                else -> {
                    LazyColumn {
                        items(state.items, key = { "${it.name}-${it.lat}-${it.lng}" }) { poi ->
                            ResultRow(
                                poi = poi,
                                center = center,
                                onClick = {
                                    vm.selectPoi(poi)
                                    onSelectPoi(poi)
                                },
                            )
                        }
                        if (state.hasMore) {
                            item {
                                GpButton(
                                    text = if (state.loading) strings.place.loading else strings.place.loadMore,
                                    onClick = vm::loadMore,
                                    style = GpButtonStyle.Ghost,
                                    enabled = !state.loading,
                                    height = 42.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(poi: Poi, center: GeoPoint, onClick: () -> Unit) {
    val distance = GeoUtils.distanceMeters(center, GeoPoint(poi.lat, poi.lng))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFFF3F7FF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                categoryIcon(poi.category),
                contentDescription = null,
                tint = Gp.Blue,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(poi.name, color = Gp.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(poi.address, color = Gp.Ink3, fontSize = 11.5.sp, maxLines = 1)
        }
        Text(
            text = GeoUtils.formatDistance(distance),
            color = Gp.Blue700,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    GpDivider()
}

private fun categoryIcon(category: PoiCategory) = when (category) {
    PoiCategory.SCENIC -> GpIcons.Mountain
    PoiCategory.FOOD -> GpIcons.Food
    PoiCategory.TRANSIT -> GpIcons.Train
    PoiCategory.MALL -> GpIcons.Mall
    PoiCategory.HOTEL -> GpIcons.Hotel
    PoiCategory.HOSPITAL -> GpIcons.Hospital
    else -> GpIcons.Pin
}
