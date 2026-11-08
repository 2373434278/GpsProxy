package com.gpsproxy.dev.platform.time

import android.os.SystemClock
import com.gpsproxy.dev.core.time.Clock

/**
 * 生产时钟：单调时间用 [SystemClock.elapsedRealtime]，墙钟用
 * [System.currentTimeMillis]。
 *
 * 两者都不受用户手动改系统时间影响的是前者；后者仅用于展示与持久化，
 * 混用会让"暂停时长"被算进轨迹位移，因此必须由同一个端口成对提供。
 */
class AndroidClock : Clock {

    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()

    override fun wallMillis(): Long = System.currentTimeMillis()
}
