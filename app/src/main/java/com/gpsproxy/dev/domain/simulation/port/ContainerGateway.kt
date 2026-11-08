package com.gpsproxy.dev.domain.simulation.port

import com.gpsproxy.dev.domain.simulation.cell.CellSpec

/**
 * 虚拟容器定位代理端口。
 *
 * 宿主只描述"要写什么"，不关心 BlackBox 的 Binder 细节：
 * - 生产实现是 [com.gpsproxy.dev.platform.container.BlackBoxContainerGateway]；
 * - 容器不可用时装配空对象实现，所有方法安全失败，不再到处 `if (available)`；
 * - 单测可以用内存 Fake 模拟"首次失败、重试成功"等路径。
 *
 * 所有方法都由调用方在后台线程执行；实现内部不做线程切换，避免隐藏的阻塞。
 */
interface ContainerGateway {

    /** 容器运行时是否可用（引擎已挂载且 :black 服务可连通）。 */
    val available: Boolean

    /** 为所有已导入应用开启跟随模式，返回成功数量。 */
    fun enableAll(): Int

    /** 关闭所有应用的模拟，恢复真实定位。 */
    fun disableAll()

    /** 写入全局坐标；失败时实现负责清理失效的 Binder 缓存。 */
    fun push(sample: LocationSample): Boolean

    /** 写入与坐标同城的基站指纹。 */
    fun pushCell(cell: CellSpec): Boolean

    /** 清空基站数据，避免关闭模拟后残留伪造基站。 */
    fun clearCell(): Boolean

    /** 只读探测定位代理是否可读写。 */
    fun probe(): Boolean

    /** 写入某个应用的专属坐标（OWN 模式），用于按应用独立设定位置。 */
    fun setAppLocation(packageName: String, sample: LocationSample): Boolean
}
