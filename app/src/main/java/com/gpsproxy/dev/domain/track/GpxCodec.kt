package com.gpsproxy.dev.domain.track

import com.gpsproxy.dev.core.i18n.StringsHolder

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 一条导入/导出的轨迹。
 *
 * [elevations] 与 [points] 等长；缺失海拔时为 null。导入侧只保留这两个数组，
 * 不承诺保留 GPX 的扩展字段——本项目只用轨迹形状与海拔。
 */
data class GpxTrack(
    val name: String,
    val points: List<GeoPoint>,
    val elevations: List<Double?> = emptyList(),
)

/**
 * GPX 1.0/1.1 的纯编解码。
 *
 * 安全与容量约束：
 * - 先按字符数拒绝超大输入，避免解析器把内存吃光；
 * - 禁用 DTD 与外部实体，阻止 XXE；
 * - 最多保留 [MAX_POINTS] 个点（等间隔降采样，首尾保留）。
 *
 * 只读取 `trkpt` 与 `rtept`；`wpt` 是单个路点，不属于轨迹。
 */
object GpxCodec {

    /** 10 MB 文本上限；GPS 轨迹远超这个体量时更适合先裁剪。 */
    const val MAX_XML_CHARS = 10 * 1024 * 1024

    /** 解析后保留的最大点数。 */
    const val MAX_POINTS = 20_000

    /** 解析前的原始点数上限，防止恶意文件制造巨量对象。 */
    private const val MAX_RAW_POINTS = 200_000

    fun parse(xml: String): GpxTrack? {
        if (xml.isBlank() || xml.length > MAX_XML_CHARS) return null
        // 显式拒绝 DOCTYPE：不同平台对 disallow-doctype-decl 的支持并不一致，
        // 预检查能保证 XXE 在所有实现上都被挡住。
        if (xml.contains("<!DOCTYPE", ignoreCase = true)) return null
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { isXIncludeAware = false }
                // 不同实现支持的特性不同，逐项尝试，失败不影响后续解析。
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val nodes = document.getElementsByTagName("*")
            val points = ArrayList<GeoPoint>()
            val elevations = ArrayList<Double?>()
            for (index in 0 until nodes.length) {
                if (points.size >= MAX_RAW_POINTS) break
                val element = nodes.item(index) as? Element ?: continue
                val local = localName(element)
                if (local != "trkpt" && local != "rtept") continue
                val lat = element.getAttribute("lat").toDoubleOrNull() ?: continue
                val lon = element.getAttribute("lon").toDoubleOrNull() ?: continue
                val point = GeoPoint(lat, lon)
                if (!point.isValid()) continue
                if (points.lastOrNull() == point) continue
                points.add(point)
                elevations.add(childText(element, "ele")?.toDoubleOrNull())
            }
            if (points.size < 2) return@runCatching null
            val name = firstText(nodes, "name").orEmpty().take(120)
            val trimmedPoints: List<GeoPoint>
            val trimmedElevations: List<Double?>
            if (points.size > MAX_POINTS) {
                val sampled = sampleIndices(points.size, MAX_POINTS)
                trimmedPoints = sampled.map(points::get)
                trimmedElevations = sampled.map { elevations[it] }
            } else {
                trimmedPoints = points
                trimmedElevations = elevations
            }
            GpxTrack(
                name = name.ifBlank { StringsHolder.text { route.importedTrack } },
                points = trimmedPoints,
                elevations = trimmedElevations,
            )
        }.getOrNull()
    }

    fun write(name: String, points: List<GeoPoint>, elevations: List<Double?>? = null): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<gpx version=\"1.1\" creator=\"GpsProxy\" ")
            .append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        builder.append("  <trk>\n")
        builder.append("    <name>").append(escape(name)).append("</name>\n")
        builder.append("    <trkseg>\n")
        points.forEachIndexed { index, point ->
            builder.append("      <trkpt lat=\"").append(coordinate(point.lat))
                .append("\" lon=\"").append(coordinate(point.lng)).append("\">")
            elevations?.getOrNull(index)?.let { elevation ->
                if (elevation.isFinite()) {
                    builder.append("<ele>").append(coordinate(elevation)).append("</ele>")
                }
            }
            builder.append("</trkpt>\n")
        }
        builder.append("    </trkseg>\n")
        builder.append("  </trk>\n")
        builder.append("</gpx>\n")
        return builder.toString()
    }

    /** 地图与持久化用的降采样入口；与 [parse] 的上限保持一致。 */
    fun simplify(points: List<GeoPoint>, maxPoints: Int = MAX_POINTS): List<GeoPoint> =
        GeoUtils.downsample(points, maxPoints)

    private fun localName(element: Element): String =
        (element.localName ?: element.nodeName.substringAfter(':')).lowercase(Locale.US)

    private fun firstText(nodes: org.w3c.dom.NodeList, local: String): String? {
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            if (localName(element) == local) {
                val value = element.textContent?.trim()
                if (!value.isNullOrEmpty()) return value
            }
        }
        return null
    }

    private fun childText(parent: Element, local: String): String? {
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val node: Node = children.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            if (localName(element) == local) return element.textContent?.trim()
        }
        return null
    }

    private fun sampleIndices(size: Int, maxPoints: Int): List<Int> {
        if (size <= maxPoints) return (0 until size).toList()
        val step = (size - 1).toDouble() / (maxPoints - 1)
        return (0 until maxPoints).map { (it * step).toInt().coerceIn(0, size - 1) }
            .distinct()
            .ifEmpty { listOf(0, size - 1) }
    }

    private fun coordinate(value: Double): String =
        String.format(Locale.US, "%.7f", value)

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
