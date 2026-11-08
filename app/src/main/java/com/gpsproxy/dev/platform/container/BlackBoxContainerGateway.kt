package com.gpsproxy.dev.platform.container

import com.gpsproxy.dev.container.ContainerEngine
import com.gpsproxy.dev.domain.simulation.cell.CellNetworkType
import com.gpsproxy.dev.domain.simulation.cell.CellSpec
import com.gpsproxy.dev.domain.simulation.port.ContainerGateway
import com.gpsproxy.dev.domain.simulation.port.LocationSample
import top.niunaijun.blackbox.entity.location.BCell

/**
 * BlackBox 容器定位代理的适配器。
 *
 * 把领域层的位置载荷翻译成 `BLocation` / `BCell`，并复用 [ContainerEngine]
 * 现有的 Binder 异常隔离策略（写入失败时清理失效服务缓存，下一帧自动重连）。
 *
 * 之所以单独一层而不是让领域直接调 `ContainerEngine`：
 * - 领域不需要知道 BlackBox 的类型，单测可注入内存 Fake；
 * - 容器不可用时可以装配空对象实现，避免到处 `if (available)`；
 * - 未来替换虚拟化引擎只需换一个适配器。
 */
class BlackBoxContainerGateway : ContainerGateway {

    override val available: Boolean
        get() = ContainerEngine.available

    override fun enableAll(): Int = ContainerEngine.enableAll()

    override fun disableAll() = ContainerEngine.disableAll()

    override fun push(sample: LocationSample): Boolean = ContainerEngine.pushLocation(
        point = sample.point,
        accuracyMeters = sample.accuracyMeters.toFloat(),
        altitude = sample.altitude,
        speedMps = sample.speedMps,
        bearing = sample.bearing,
        coordSystem = sample.coordSystem,
        satellites = sample.satellites,
    )

    override fun pushCell(cell: CellSpec): Boolean = ContainerEngine.pushCell(cell.toBCell())

    override fun clearCell(): Boolean = ContainerEngine.clearCell()

    override fun probe(): Boolean = ContainerEngine.probeLocationProxy()

    override fun setAppLocation(packageName: String, sample: LocationSample): Boolean =
        ContainerEngine.pushAppLocation(
            packageName = packageName,
            point = sample.point,
            accuracyMeters = sample.accuracyMeters.toFloat(),
            altitude = sample.altitude,
            speedMps = sample.speedMps,
            bearing = sample.bearing,
            coordSystem = sample.coordSystem,
            satellites = sample.satellites,
        )

    private fun CellSpec.toBCell(): BCell = BCell(mcc, mnc, lac, cid).apply {
        TYPE = BCell.PHONE_TYPE_GSM
        networkType = when (this@toBCell.networkType) {
            CellNetworkType.LTE -> BCell.NETWORK_TYPE_LTE
            CellNetworkType.UMTS -> BCell.NETWORK_TYPE_UMTS
        }
        signalStrengthDbm = this@toBCell.signalStrengthDbm
        signalLevel = this@toBCell.signalLevel
        registered = this@toBCell.registered
    }
}
