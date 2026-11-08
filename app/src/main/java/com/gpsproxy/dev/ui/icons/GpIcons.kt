package com.gpsproxy.dev.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object GpIcons {

    private fun stroke(name: String, vararg data: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            data.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun filled(name: String, data: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = SolidColor(Color.Black),
                stroke = null,
            )
        }.build()

    val Pin = stroke(
        "pin",
        "M12 21s7-5.8 7-11a7 7 0 1 0-14 0c0 5.2 7 11 7 11z",
        "M14.4 10a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 0 1 4.8 0z",
    )
    val PinFilled = filled(
        "pin-filled",
        "M12 22s7.3-6 7.3-11.4a7.3 7.3 0 1 0-14.6 0C4.7 16 12 22 12 22zM12 8.2a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5z",
    )
    val Search = stroke("search", "M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0z", "M16.5 16.5L21 21")
    val Star = stroke(
        "star",
        "M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8L3.5 9.7l5.9-.8z",
    )
    val StarFilled = filled(
        "star-filled",
        "M12 3.2l2.8 5.7 6.2 0.9-4.5 4.4 1.1 6.2L12 17.5l-5.6 2.9 1.1-6.2L3 9.8l6.2-0.9z",
    )
    val Route = stroke(
        "route",
        "M4 19c0-3 2.5-3.4 2.5-6.4S4 9.2 4 6.4",
        "M4 4.2v.2",
        "M20 19h-8.6",
        "M6 4a2 2 0 1 1-4 0 2 2 0 0 1 4 0z",
        "M20 19a2 2 0 1 1-4 0 2 2 0 0 1 4 0z",
    )
    val Grid = stroke(
        "grid",
        "M6 4h2a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M16 4h2a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M6 14h2a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2z",
        "M16 14h2a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2z",
    )
    val Settings = stroke(
        "settings",
        "M15.2 12a3.2 3.2 0 1 1-6.4 0 3.2 3.2 0 0 1 6.4 0z",
        "M12 3.2v2",
        "M12 18.8v2",
        "M3.2 12h2",
        "M18.8 12h2",
        "M5.8 5.8l1.4 1.4",
        "M16.8 16.8l1.4 1.4",
        "M18.2 5.8l-1.4 1.4",
        "M7.2 16.8l-1.4 1.4",
    )
    val Shield = stroke(
        "shield",
        "M12 3l7 3v6c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9V6z",
        "M12 9v3.5",
        "M12 15.6v.2",
    )
    val Back = stroke("back", "M15 6l-6 6 6 6")
    val ChevronRight = stroke("chevron-right", "M9 6l6 6-6 6")
    val Plus = stroke("plus", "M12 5v14", "M5 12h14")
    val Minus = stroke("minus", "M5 12h14")
    val ArrowUp = stroke("arrow-up", "M12 19V5", "M6 11l6-6 6 6")
    val ArrowDown = stroke("arrow-down", "M12 5v14", "M6 13l6 6 6-6")
    val Copy = stroke("copy", "M9 9h10v10H9z", "M5 15V5h10")
    val Map = stroke(
        "map",
        "M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z",
        "M9 3v15",
        "M15 6v15",
    )
    val Bell = stroke(
        "bell",
        "M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7z",
        "M10 20a2 2 0 0 0 4 0",
    )
    val Clock = stroke("clock", "M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0z", "M12 8v4l2.6 1.6")
    val Upload = stroke("upload", "M12 3v11", "M8 10l4 4 4-4", "M4 19h16")
    val Download = stroke("download", "M12 3v11", "M8 10l4 4 4-4", "M4 19h16")
    val Pause = stroke("pause", "M9 5v14", "M15 5v14")
    val Play = filled("play", "M8 5l11 7-11 7z")
    val Stop = filled("stop", "M6 6h12v12H6z")
    val Close = stroke("close", "M6 6l12 12", "M18 6L6 18")
    val Crosshair = stroke(
        "crosshair",
        "M18.5 12a6.5 6.5 0 1 1-13 0 6.5 6.5 0 0 1 13 0z",
        "M12 3.5v3",
        "M12 17.5v3",
        "M3.5 12h3",
        "M17.5 12h3",
    )
    val Trash = stroke("trash", "M4 7h16", "M9 7V5h6v2", "M6 7l1 13h10l1-13")
    val Edit = stroke("edit", "M4 20h4L20 8l-4-4L4 16z", "M14 6l4 4")
    val Info = stroke("info", "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z", "M12 11v5", "M12 8v.2")
    val Check = stroke("check", "M4 12l5 5L20 6")
    val Warning = stroke("warning", "M12 3l9 16H3z", "M12 9v4", "M12 16.4v.2")
    val Battery = stroke("battery", "M4 8h13v8H4z", "M20 11v2")
    val Refresh = stroke("refresh", "M3 12a9 9 0 1 0 3-6.7", "M3 4v5h5")
    val Target = stroke(
        "target",
        "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
        "M19 12a7 7 0 1 1-14 0 7 7 0 0 1 14 0z",
        "M12 2v2",
        "M12 20v2",
        "M2 12h2",
        "M20 12h2",
    )
    val Mountain = stroke("mountain", "M3 19l6-10 4 6 2-3 6 7z")
    val Food = stroke("food", "M6 3v8a2 2 0 0 0 4 0V3", "M8 11v10", "M16 3c-1.5 2-2 3.5-2 6h4c0-2.5-.5-4-2-6z", "M16 9v12")
    val Train = stroke("train", "M7 3h10a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3z", "M4 10h16", "M9 20l1.6-4", "M15 20l-1.6-4")
    val Mall = stroke("mall", "M4 9h16l-1.4 10H5.4z", "M8.6 9V7a3.4 3.4 0 0 1 6.8 0v2")
    val Hotel = stroke("hotel", "M4 20V8h16v12", "M4 12h16", "M8 8V5h3v3", "M13 8V5h3v3")
    val Hospital = stroke("hospital", "M4 20V6h16v14", "M12 9v6", "M9 12h6")
    val Layers = stroke("layers", "M12 3l9 5-9 5-9-5z", "M3 13l9 5 9-5", "M3 17l9 5 9-5")
    val Locate = stroke("locate", "M12 3v3", "M12 18v3", "M3 12h3", "M18 12h3", "M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0z")
    val ShieldCheck = stroke("shield-check", "M12 3l7 3v6c0 4.4-3 7.6-7 9-4-1.4-7-4.6-7-9V6z", "M8.5 12l2.4 2.4 4.6-4.8")
}
