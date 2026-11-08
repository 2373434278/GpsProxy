package com.gpsproxy.dev.core.time

import kotlinx.coroutines.delay

/**
 * 时间的唯一入口。
 *
 * 模拟循环里混用了 `SystemClock.elapsedRealtime()`（单调，用于计算帧间隔）
 * 与 `System.currentTimeMillis()`（墙钟，用于展示与持久化）。两者语义不同，
 * 直接在业务代码里写死会让时间相关的分支无法在单测中复现。
 *
 * 通过该端口注入：生产环境用 Android 单调时钟，测试用可推进的假时钟。
 */
interface Clock {

    /** 单调递增的毫秒数，只能用于测量时间差，不可用于展示。 */
    fun elapsedMillis(): Long

    /** 墙钟毫秒数（Unix epoch），用于展示、持久化与日志。 */
    fun wallMillis(): Long

    /** 挂起指定毫秒。测试时钟可立即推进，避免真实等待。 */
    suspend fun delay(millis: Long) {
        // 必须全限定：接口自身的 delay 会遮蔽顶层同名函数并造成递归调用。
        kotlinx.coroutines.delay(millis)
    }
}
