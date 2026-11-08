package com.gpsproxy.dev.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.theme.Gp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

@Stable
class MapCameraState(center: GeoPoint, zoom: Float) {
    var center by mutableStateOf(center)
    var zoom by mutableStateOf(zoom)

    fun moveTo(point: GeoPoint) {
        center = point
    }
}

@Composable
fun rememberMapCamera(center: GeoPoint, zoom: Float = 15f): MapCameraState =
    remember { MapCameraState(center, zoom) }

private const val TILE_SIZE = 256.0
private const val MAX_LAT = 85.05112878

private fun worldSize(zoom: Float): Double = TILE_SIZE * 2.0.pow(zoom.toDouble())

private fun projectRaw(point: GeoPoint, zoom: Float): Offset {
    val world = worldSize(zoom)
    val lat = point.lat.coerceIn(-MAX_LAT, MAX_LAT)
    val x = (point.lng + 180.0) / 360.0 * world
    val latRad = Math.toRadians(lat)
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * world
    return Offset(x.toFloat(), y.toFloat())
}

private fun unprojectRaw(worldPoint: Offset, zoom: Float): GeoPoint {
    val world = worldSize(zoom)
    val lng = worldPoint.x / world * 360.0 - 180.0
    val n = PI - 2.0 * PI * worldPoint.y / world
    val lat = Math.toDegrees(atan(sinh(n)))
    return GeoPoint(lat, lng)
}

private fun geoToScreen(point: GeoPoint, center: GeoPoint, zoom: Float, size: IntSize): Offset {
    val world = projectRaw(point, zoom)
    val centerWorld = projectRaw(center, zoom)
    return Offset(
        world.x - centerWorld.x + size.width / 2f,
        world.y - centerWorld.y + size.height / 2f,
    )
}

private fun screenToGeo(offset: Offset, center: GeoPoint, zoom: Float, size: IntSize): GeoPoint {
    val centerWorld = projectRaw(center, zoom)
    val world = Offset(
        centerWorld.x + offset.x - size.width / 2f,
        centerWorld.y + offset.y - size.height / 2f,
    )
    return unprojectRaw(world, zoom)
}

private val LAT_STEPS = doubleArrayOf(
    0.0002, 0.0005, 0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5,
)

private fun niceStep(desired: Double): Double =
    LAT_STEPS.firstOrNull { it >= desired } ?: LAT_STEPS.last()

private fun cellHash(a: Int, b: Int): Int {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h shr 13)) * 1274126177
    return h xor (h shr 16)
}

private val STREET_NAMES = arrayOf(
    "文三路", "学院路", "天目山路", "教工路", "莫干山路", "文一路", "古翠路", "丰潭路",
)

@Composable
fun SchematicMap(
    modifier: Modifier,
    camera: MapCameraState,
    marker: GeoPoint?,
    markerColor: Color = Gp.Blue,
    routePoints: List<GeoPoint> = emptyList(),
    dashedRoute: Boolean = true,
    routeStart: GeoPoint? = null,
    routeEnd: GeoPoint? = null,
    waypoints: List<GeoPoint> = emptyList(),
    accuracyMeters: Float? = null,
    showCrosshair: Boolean = false,
    onLongPress: ((GeoPoint) -> Unit)? = null,
    onCenterChanged: (GeoPoint) -> Unit = {},
    followMarker: Boolean = false,
    zoomControls: Boolean = false,
    watermark: String? = LocalStrings.current.home.mapWatermarkDefault,
    interactive: Boolean = true,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    LaunchedEffect(followMarker, marker) {
        if (followMarker && marker != null) camera.center = marker
    }
    LaunchedEffect(camera.center, camera.zoom) {
        onCenterChanged(camera.center)
    }

    Box(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .then(
                    if (interactive) {
                        Modifier.pointerInput(camera, canvasSize) {
                            detectTransformGestures { centroid, pan, zoomChange, _ ->
                                if (canvasSize.width == 0) return@detectTransformGestures
                                val anchor = screenToGeo(centroid, camera.center, camera.zoom, canvasSize)
                                val newZoom = (camera.zoom + log2(zoomChange.coerceAtLeast(0.01f)))
                                    .coerceIn(3f, 19f)
                                val centerWorld = projectRaw(camera.center, newZoom) - pan
                                val tempCenter = unprojectRaw(centerWorld, newZoom)
                                val after = geoToScreen(anchor, tempCenter, newZoom, canvasSize)
                                val correction = after - centroid
                                val finalCenter = unprojectRaw(
                                    projectRaw(tempCenter, newZoom) + correction,
                                    newZoom,
                                )
                                camera.zoom = newZoom
                                camera.center = finalCenter
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (interactive && onLongPress != null) {
                        Modifier.pointerInput(camera, canvasSize, onLongPress) {
                            detectTapGestures(
                                onLongPress = { offset ->
                                    if (canvasSize.width > 0) {
                                        onLongPress(
                                            screenToGeo(offset, camera.center, camera.zoom, canvasSize)
                                        )
                                    }
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (canvasSize.width == 0) return@Canvas
            val center = camera.center
            val zoom = camera.zoom
            val size = canvasSize

            drawRect(Gp.MapBase)

            val metersPerPixel = 156543.03392 *
                cos(Math.toRadians(center.lat)) / 2.0.pow(zoom.toDouble())
            val latStep = niceStep(metersPerPixel * 90.0 / 111_320.0)
            val lngStep = latStep / cos(Math.toRadians(center.lat)).coerceAtLeast(0.15)

            val topLeft = screenToGeo(Offset.Zero, center, zoom, size)
            val bottomRight = screenToGeo(Offset(size.width.toFloat(), size.height.toFloat()), center, zoom, size)
            val latStart = floor(bottomRight.lat / latStep) * latStep
            val lngStart = floor(topLeft.lng / lngStep) * lngStep
            val latCount = ((topLeft.lat - latStart) / latStep).toInt().coerceIn(0, 80)
            val lngCount = ((bottomRight.lng - lngStart) / lngStep).toInt().coerceIn(0, 80)

            for (i in 0..latCount) {
                for (j in 0..lngCount) {
                    val latHigh = latStart + (i + 1) * latStep
                    val lngRight = lngStart + (j + 1) * lngStep
                    val p1 = geoToScreen(GeoPoint(latHigh, lngStart + j * lngStep), center, zoom, size)
                    val p2 = geoToScreen(GeoPoint(latStart + i * latStep, lngRight), center, zoom, size)
                    val rectTopLeft = Offset(p1.x, p1.y)
                    val rectSize = Size(p2.x - p1.x, p2.y - p1.y)
                    if (rectSize.width <= 0f || rectSize.height <= 0f) continue
                    val hash = cellHash(i + (latStart / latStep).roundToInt(), j + (lngStart / lngStep).roundToInt())
                    val bucket = abs(hash) % 23
                    val color = when {
                        bucket == 0 -> Gp.MapPark
                        bucket == 1 -> Gp.MapWater
                        bucket % 3 == 0 -> Color(0xFFE2E9F0)
                        else -> Color(0xFFE6EDF3)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = rectTopLeft,
                        size = rectSize,
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }

            val edgeWidth = 14.dp.toPx()
            val roadWidth = 10.dp.toPx()
            val majorEdge = 22.dp.toPx()
            val majorRoad = 17.dp.toPx()

            for (i in 0..latCount) {
                val lat = latStart + i * latStep
                val y = geoToScreen(GeoPoint(lat, center.lng), center, zoom, size).y
                if (y < -30f || y > size.height + 30f) continue
                val index = (lat / latStep).roundToInt()
                val major = index % 4 == 0
                drawLine(
                    color = Gp.MapRoadEdge,
                    start = Offset(0f, y),
                    end = Offset(size.width.toFloat(), y),
                    strokeWidth = if (major) majorEdge else edgeWidth,
                )
                drawLine(
                    color = Gp.MapRoad,
                    start = Offset(0f, y),
                    end = Offset(size.width.toFloat(), y),
                    strokeWidth = if (major) majorRoad else roadWidth,
                )
                if (major) {
                    drawLine(
                        color = Color(0xFFF6D7A4),
                        start = Offset(0f, y),
                        end = Offset(size.width.toFloat(), y),
                        strokeWidth = 1.4.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 9f)),
                    )
                }
                if (index % 3 == 0) {
                    val name = STREET_NAMES[abs(index) % STREET_NAMES.size]
                    drawText(
                        textMeasurer = textMeasurer,
                        text = name,
                        topLeft = Offset(10.dp.toPx(), y - 18.dp.toPx()),
                        style = TextStyle(fontSize = 9.sp, color = Color(0xFF8A94A3)),
                    )
                }
            }

            for (j in 0..lngCount) {
                val lng = lngStart + j * lngStep
                val x = geoToScreen(GeoPoint(center.lat, lng), center, zoom, size).x
                if (x < -30f || x > size.width + 30f) continue
                val index = (lng / lngStep).roundToInt()
                val major = index % 4 == 0
                drawLine(
                    color = Gp.MapRoadEdge,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height.toFloat()),
                    strokeWidth = if (major) majorEdge else edgeWidth,
                )
                drawLine(
                    color = Gp.MapRoad,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height.toFloat()),
                    strokeWidth = if (major) majorRoad else roadWidth,
                )
            }

            if (routePoints.size > 1) {
                val path = Path()
                routePoints.forEachIndexed { index, point ->
                    val screen = geoToScreen(point, center, zoom, size)
                    if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
                }
                drawPath(
                    path = path,
                    color = Gp.Blue.copy(alpha = .16f),
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                )
                drawPath(
                    path = path,
                    color = Gp.Blue,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = if (dashedRoute) {
                            PathEffect.dashPathEffect(floatArrayOf(1f, 9f))
                        } else {
                            null
                        },
                    ),
                )
            }

            waypoints.forEach { point ->
                val screen = geoToScreen(point, center, zoom, size)
                drawCircle(Color.White, 5.dp.toPx(), screen)
                drawCircle(Color(0xFF98A2B3), 3.dp.toPx(), screen)
            }

            routeStart?.let { drawPin(geoToScreen(it, center, zoom, size), Gp.Green, density.density) }
            routeEnd?.let { drawPin(geoToScreen(it, center, zoom, size), Gp.Red, density.density) }

            if (marker != null) {
                val screen = geoToScreen(marker, center, zoom, size)
                if (accuracyMeters != null) {
                    val radius = (accuracyMeters / metersPerPixel).toFloat().coerceIn(18f, 320f)
                    drawCircle(Gp.Blue.copy(alpha = .10f), radius, screen)
                    drawCircle(
                        color = Gp.Blue.copy(alpha = .26f),
                        radius = radius,
                        center = screen,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                if (showCrosshair) {
                    val arm = 22.dp.toPx()
                    drawLine(
                        Gp.Blue.copy(alpha = .5f),
                        Offset(screen.x - arm, screen.y),
                        Offset(screen.x + arm, screen.y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        Gp.Blue.copy(alpha = .5f),
                        Offset(screen.x, screen.y - arm),
                        Offset(screen.x, screen.y + arm),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                drawCircle(Color.Black.copy(alpha = .14f), 12.dp.toPx(), screen + Offset(0f, 2.dp.toPx()))
                drawCircle(Color.White, 11.dp.toPx(), screen)
                drawCircle(markerColor, 8.dp.toPx(), screen)
            }
        }

        if (watermark != null) {
            Text(
                text = watermark,
                color = Color(0xFF8A94A3),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 8.dp)
                    .background(Color.White.copy(alpha = .72f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        if (zoomControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MapControl(GpIcons.Plus, LocalStrings.current.home.mapZoomIn) { camera.zoom = (camera.zoom + 1f).coerceAtMost(19f) }
                MapControl(GpIcons.Minus, LocalStrings.current.home.mapZoomOut) { camera.zoom = (camera.zoom - 1f).coerceAtLeast(3f) }
                MapControl(GpIcons.Locate, LocalStrings.current.home.mapRecenter) {
                    marker?.let { camera.center = it }
                }
            }
        }
    }
}

@Composable
private fun MapControl(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = .97f),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 8.dp,
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color(0xFF344054), modifier = Modifier.size(19.dp))
        }
    }
}

private fun DrawScope.drawPin(center: Offset, color: Color, density: Float) {
    drawCircle(Color.Black.copy(alpha = .16f), 8.dp.toPx(), center + Offset(0f, 2.dp.toPx()))
    drawCircle(Color.White, 7.dp.toPx(), center)
    drawCircle(color, 5.dp.toPx(), center)
}
