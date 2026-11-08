package com.gpsproxy.dev.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.CoordSystem
import com.gpsproxy.dev.core.geo.CoordTransform
import com.gpsproxy.dev.core.geo.CoordinateParser
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.domain.place.DefaultLocation
import com.gpsproxy.dev.ui.feature.pick.PickViewModel
import com.gpsproxy.dev.ui.components.ErrorCard
import com.gpsproxy.dev.ui.components.FinePrint
import com.gpsproxy.dev.ui.components.GpButton
import com.gpsproxy.dev.ui.components.GpButtonStyle
import com.gpsproxy.dev.ui.components.GpIconButton
import com.gpsproxy.dev.ui.components.GpTextField
import com.gpsproxy.dev.ui.components.StatusChip
import com.gpsproxy.dev.ui.components.StatusTone
import com.gpsproxy.dev.ui.components.SurfaceCard
import com.gpsproxy.dev.ui.components.ValuePill
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.map.SchematicMap
import com.gpsproxy.dev.ui.map.rememberMapCamera
import com.gpsproxy.dev.ui.theme.Gp
import java.util.Locale
import kotlin.math.abs

@Composable
fun PickScreen(
    vm: PickViewModel,
    mode: String,
    onBack: () -> Unit,
    onConfirmed: () -> Unit,
) {
    val draft by vm.draftPoint.collectAsStateWithLifecycle()
    val directAddress by vm.draftAddress.collectAsStateWithLifecycle()
    val reverseAddress by vm.reverseAddress.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val point = draft ?: DefaultLocation.POINT
    val camera = rememberMapCamera(point)
    LaunchedEffect(point) { camera.center = point }

    val isRouteEnd = mode == "routeEnd"
    val isRouteWaypoint = mode == "routeWaypoint"
    val isRoutePick = isRouteEnd || isRouteWaypoint
    val displayed = remember(point, settings.coordSystem) {
        CoordTransform.transform(point, CoordSystem.GCJ02, settings.coordSystem)
    }
    var latText by remember { mutableStateOf(formatCoordinate(displayed.lat)) }
    var lngText by remember { mutableStateOf(formatCoordinate(displayed.lng)) }
    LaunchedEffect(displayed) {
        latText = formatCoordinate(displayed.lat)
        lngText = formatCoordinate(displayed.lng)
    }

    val latValue = latText.toDoubleOrNull()
    val lngValue = lngText.toDoubleOrNull()
    val latError = latText.isNotBlank() && (latValue == null || latValue !in -90.0..90.0)
    val lngError = lngText.isNotBlank() && (lngValue == null || lngValue !in -180.0..180.0)
    val valid = latValue != null && lngValue != null && !latError && !lngError && latText.isNotBlank() && lngText.isNotBlank()
    val displayAddress = directAddress.ifBlank {
        reverseAddress ?: strings.place.reverseResolving
    }

    fun nudge(north: Double, east: Double) {
        vm.setDraftPoint(GeoUtils.offsetMeters(point, north, east), null)
    }

    fun confirm(): Boolean {
        if (!valid) {
            vm.reportInvalidCoordinate()
            return false
        }
        val typed = GeoPoint(latValue!!, lngValue!!)
        val canonical = CoordTransform.transform(typed, settings.coordSystem, CoordSystem.GCJ02)
        val knownAddress = displayAddress
        val changed = abs(canonical.lat - point.lat) > 1e-6 || abs(canonical.lng - point.lng) > 1e-6
        val address = if (changed) {
            strings.place.mockPickAddress(canonical.formatted())
        } else {
            knownAddress
        }
        vm.setDraftPoint(canonical, address)
        when {
            isRouteWaypoint -> vm.setRouteWaypoint(canonical, address)
            isRouteEnd -> vm.setRouteEnd(canonical, address)
            else -> vm.applyPickAsSimulation()
        }
        return true
    }

    Box(Modifier.fillMaxSize()) {
        SchematicMap(
            modifier = Modifier.fillMaxSize(),
            camera = camera,
            marker = point,
            accuracyMeters = settings.accuracyMeters.toFloat(),
            showCrosshair = true,
            onLongPress = { vm.setDraftPoint(it, null) },
            zoomControls = true,
            watermark = strings.place.pickWatermark,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GpIconButton(
                icon = GpIcons.Back,
                contentDescription = strings.common.back,
                onClick = onBack,
                background = Color.White,
                tint = Gp.Ink,
            )
            Spacer(Modifier.weight(1f))
            if (isRouteEnd) {
                StatusChip(text = strings.place.chipRouteEnd, tone = StatusTone.Active)
                Spacer(Modifier.width(8.dp))
            } else if (isRouteWaypoint) {
                StatusChip(text = strings.place.chipWaypoint, tone = StatusTone.Active)
                Spacer(Modifier.width(8.dp))
            }
            StatusChip(text = settings.coordSystem.label, tone = StatusTone.Idle, mono = true)
            Spacer(Modifier.width(8.dp))
            GpIconButton(
                icon = GpIcons.Map,
                contentDescription = strings.home.externalMap,
                onClick = vm::openInExternalMap,
                background = Color.White,
                tint = Gp.Ink,
            )
        }

        SurfaceCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 14.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = 20.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        ) {
            Box(
                Modifier
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFFE0E5EC))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = displayAddress,
                    color = Gp.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Gp.Blue50)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(strings.place.reverseTag, color = Gp.Blue700, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${vm.displayCoords(point)} · ${settings.coordSystem.label}",
                color = Gp.Ink3,
                fontSize = 11.5.sp,
            )
            if (settings.blurEnabled) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = strings.place.blurNotice(settings.blurRadiusMeters),
                    color = Gp.Orange,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValuePill(
                    text = strings.place.pasteCoords,
                    onClick = {
                        val text = clipboard.getText()?.text
                        if (text.isNullOrBlank()) {
                            vm.reportEmptyClipboard()
                            return@ValuePill
                        }
                        val parsed = CoordinateParser.parse(text)
                        if (parsed == null) {
                            vm.reportUnparsableCoordinate()
                        } else {
                            val source = parsed.coordSystem ?: settings.coordSystem
                            val converted = CoordTransform.transform(
                                parsed.point,
                                source,
                                settings.coordSystem,
                            )
                            latText = formatCoordinate(converted.lat)
                            lngText = formatCoordinate(converted.lng)
                        }
                    },
                )
                ValuePill(
                    text = strings.place.copy,
                    onClick = {
                        clipboard.setText(AnnotatedString(vm.displayCoords(point)))
                    },
                )
            }
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NudgePad(onNudge = ::nudge)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldWithLabel(strings.place.latitude, latText, { latText = it }, latError)
                    FieldWithLabel(strings.place.longitude, lngText, { lngText = it }, lngError)
                }
            }

            if (latError || lngError) {
                Spacer(Modifier.height(10.dp))
                ErrorCard(
                    title = "${ErrorCode.COORD_0101.code} · ${strings.error.title(ErrorCode.COORD_0101)}",
                    message = strings.place.invalidRangeMessage,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isRoutePick) {
                    GpButton(
                        text = strings.common.cancel,
                        onClick = onBack,
                        style = GpButtonStyle.Outline,
                        modifier = Modifier.weight(1f),
                    )
                    GpButton(
                        text = if (isRouteWaypoint) strings.place.setAsWaypoint else strings.place.setAsEnd,
                        onClick = {
                            if (confirm()) onConfirmed()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = valid,
                    )
                } else {
                    GpButton(
                        text = strings.home.favorite,
                        onClick = {
                            if (valid) {
                                val canonical = CoordTransform.transform(
                                    GeoPoint(latValue!!, lngValue!!),
                                    settings.coordSystem,
                                    CoordSystem.GCJ02,
                                )
                                vm.setDraftPoint(canonical, null)
                                vm.saveDraftAsFavorite()
                            } else {
                                vm.reportInvalidCoordinate()
                            }
                        },
                        style = GpButtonStyle.Outline,
                        modifier = Modifier.weight(1f),
                    )
                    GpButton(
                        text = strings.place.setAsMock,
                        onClick = {
                            if (confirm()) onConfirmed()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = valid,
                    )
                }
            }
            FinePrint(
                strings.place.pickHintPrefix + if (isRouteWaypoint) {
                    strings.place.waypointOrderHint
                } else {
                    strings.place.coordSystemHint(settings.coordSystem.label)
                },
            )
        }
    }
}

@Composable
private fun FieldWithLabel(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
) {
    Column {
        Text(label, color = Gp.Ink3, fontSize = 10.5.sp)
        Spacer(Modifier.height(4.dp))
        GpTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = label,
            keyboardType = KeyboardType.Decimal,
            isError = isError,
            height = 42.dp,
        )
    }
}

@Composable
private fun NudgePad(onNudge: (north: Double, east: Double) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NudgeButton("↑") { onNudge(1.0, 0.0) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NudgeButton("←") { onNudge(0.0, -1.0) }
            NudgeButton("→") { onNudge(0.0, 1.0) }
        }
        NudgeButton("↓") { onNudge(-1.0, 0.0) }
    }
}

@Composable
private fun NudgeButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White)
            .border(1.dp, Gp.Line2, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF344054), fontSize = 13.sp)
    }
}

private fun formatCoordinate(value: Double): String =
    String.format(Locale.US, "%.6f", value)
