package com.gpsproxy.dev.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Gp {
    val Blue = Color(0xFF1677FF)
    val Blue700 = Color(0xFF0E5FD8)
    val Blue50 = Color(0xFFEAF2FF)
    val Blue100 = Color(0xFFCFE2FF)
    val Green = Color(0xFF00B578)
    val Green50 = Color(0xFFE8F8F1)
    val Orange = Color(0xFFFF8F1F)
    val Orange50 = Color(0xFFFFF4E6)
    val Red = Color(0xFFF5222D)
    val Red50 = Color(0xFFFFF1F0)
    val Ink = Color(0xFF101828)
    val Ink2 = Color(0xFF475467)
    val Ink3 = Color(0xFF98A2B3)
    val Line = Color(0xFFEEF1F6)
    val Line2 = Color(0xFFE4E9F0)
    val Surface = Color(0xFFFFFFFF)
    val Canvas = Color(0xFFF4F6F9)
    val MapBase = Color(0xFFE9EEF3)
    val MapBlock = Color(0xFFDCE5ED)
    val MapRoad = Color(0xFFFFFFFF)
    val MapRoadEdge = Color(0xFFD3DAE3)
    val MapWater = Color(0xFFC6DEF2)
    val MapPark = Color(0xFFD8EBD9)
}

private val GpColorScheme = lightColorScheme(
    primary = Gp.Blue,
    onPrimary = Color.White,
    primaryContainer = Gp.Blue50,
    onPrimaryContainer = Gp.Blue700,
    secondary = Gp.Green,
    onSecondary = Color.White,
    error = Gp.Red,
    onError = Color.White,
    errorContainer = Gp.Red50,
    onErrorContainer = Gp.Red,
    background = Gp.Canvas,
    onBackground = Gp.Ink,
    surface = Gp.Surface,
    onSurface = Gp.Ink,
    surfaceVariant = Color(0xFFF3F5F9),
    onSurfaceVariant = Gp.Ink2,
    outline = Gp.Line2,
    outlineVariant = Gp.Line,
)

private val GpTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
)

private val GpShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun GpsProxyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GpColorScheme,
        typography = GpTypography,
        shapes = GpShapes,
        content = content,
    )
}
