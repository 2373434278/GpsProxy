package com.gpsproxy.dev.domain.simulation.channel

import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.log.LogSink
import com.gpsproxy.dev.domain.settings.DetailLevel
import com.gpsproxy.dev.domain.simulation.cell.CellFingerprint
import com.gpsproxy.dev.domain.simulation.port.ContainerGateway

/**
 * 基站指纹的同步发布器。
 *
 * 只在基站真正变化（LAC/CID 换小区）时重推：服务端每次 `setGlobalCell`
 * 都会落盘一次，卫星数这类高频抖动不能作为重推条件，
 * 否则轨迹巡航时会变成每 tick 一次磁盘写入。
 *
 * 单独抽出的原因：容器直连与官方通道的"顺带同步"都需要同一份去重状态，
 * 旧实现把 `lastCellKey` 放在控制器字段里，两个写入路径共享得并不明显。
 */
class CellFingerprintPublisher(
    private val container: ContainerGateway,
    private val log: LogSink,
    private val detailLevel: () -> DetailLevel,
) {

    private var lastCellKey: String? = null

    /** @return 本次是否真的推送了基站数据 */
    fun publish(point: GeoPoint, satellites: Int): Boolean {
        val cell = CellFingerprint.derive(point, satellites)
        val key = "${cell.lac}:${cell.cid}"
        if (key == lastCellKey) return false
        lastCellKey = key
        val pushed = container.pushCell(cell)
        if (pushed && detailLevel() == DetailLevel.VERBOSE) {
            log.info { log.cellFingerprint(cell.lac, cell.cid, cell.signalStrengthDbm) }
        }
        return pushed
    }

    fun reset() {
        lastCellKey = null
    }
}
