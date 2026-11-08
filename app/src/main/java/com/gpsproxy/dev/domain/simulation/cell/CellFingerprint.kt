package com.gpsproxy.dev.domain.simulation.cell

import com.gpsproxy.dev.core.geo.GeoPoint
import kotlin.math.abs
import kotlin.math.roundToInt

/** 对外暴露的网络制式，只保留本产品实际会下发的两种。 */
enum class CellNetworkType { UMTS, LTE }

/**
 * 与模拟坐标同城的「基站指纹」。
 *
 * 这是纯数据描述，不含 BlackBox 类型；由平台适配器翻译成 `BCell` 后下发。
 * 把推导逻辑留在领域层的原因：它是可测试的确定性算法，且两个通道
 * （容器直连 / 官方 Mock 顺带同步）必须看到完全相同的值。
 */
data class CellSpec(
    val mcc: Int,
    val mnc: Int,
    val lac: Int,
    val cid: Int,
    val networkType: CellNetworkType,
    val signalStrengthDbm: Int,
    val signalLevel: Int,
    val registered: Boolean = true,
)

/**
 * 由模拟坐标推导出的「基站指纹」。
 *
 * 容器内应用除了读取 Location，还会旁路读取基站信息做一致性校验：
 * 若坐标在北京而基站落在广州，风控会直接判定为伪造。
 * 因此坐标每次变化时都要同步生成一份与坐标同城的基站参数。
 *
 * 生成策略是确定性的（同一坐标必得同一基站），这样
 * 应用在多次查询间看到稳定的 LAC/CID，不会因频繁跳变显得异常。
 */
object CellFingerprint {

    /** 中国移动 MCC。 */
    private const val MCC_CHINA = 460

    /** 中国移动 MNC，覆盖移动 0/2/7 三个常见取值。 */
    private val MNC_CANDIDATES = intArrayOf(0, 2, 7)

    /**
     * 按坐标生成基站。
     *
     * 做法是把经纬度量化成网格（约 1.1km 一格），用网格索引哈希出
     * LAC 与 CID：相邻网格的 LAC 相同、CID 不同，模拟「同一位置区内的小区切换」。
     *
     * @param point 已转换到 GCJ-02 的模拟坐标
     * @param satellites 可见卫星数，用于推导信号强度档位
     */
    fun derive(point: GeoPoint, satellites: Int = 12): CellSpec {
        // 约 0.01 度一格，约 1.1km，与城市微小区站间距同量级
        val latCell = (point.lat * 100).roundToInt()
        val lngCell = (point.lng * 100).roundToInt()
        // LAC 用较大网格（约 11km），保证一片区域内 LAC 稳定
        val lacGrid = ((point.lat * 10).roundToInt() * 31) xor (point.lng * 10).roundToInt()
        val lac = 1000 + (abs(lacGrid) % 60000)
        // CID 用细网格，位置移动即切换小区
        val cidSeed = (latCell * 73856093) xor (lngCell * 19349663)
        val cid = 1 + (abs(cidSeed) % 268435455)
        val mnc = MNC_CANDIDATES[abs(lacGrid) % MNC_CANDIDATES.size]

        return CellSpec(
            mcc = MCC_CHINA,
            mnc = mnc,
            lac = lac,
            cid = cid,
            networkType = if (satellites >= 8) CellNetworkType.LTE else CellNetworkType.UMTS,
            signalStrengthDbm = signalDbm(satellites),
            signalLevel = signalLevel(satellites),
            registered = true,
        )
    }

    /**
     * 由卫星数推导信号强度（dBm）。
     *
     * 卫星越多信号越强，范围压在 -113（最弱）到 -65（很强）之间，
     * 超出真实基站上报区间反而会暴露异常。
     */
    fun signalDbm(satellites: Int): Int {
        val strength = (-113 + satellites.coerceIn(0, 20) * 2.4).roundToInt()
        return strength.coerceIn(-113, -65)
    }

    /** 由卫星数推导 0-4 的信号等级。 */
    fun signalLevel(satellites: Int): Int = when {
        satellites >= 14 -> 4
        satellites >= 10 -> 3
        satellites >= 6 -> 2
        satellites >= 3 -> 1
        else -> 0
    }
}
