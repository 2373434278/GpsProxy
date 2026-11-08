package com.gpsproxy.dev.domain.simulation.pump

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.log.LogSink
import com.gpsproxy.dev.core.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 一帧推送的结果。 */
enum class PublishOutcome {
    /** 本帧已成功写入通道。 */
    PUBLISHED,

    /** 本帧失败但未达终止阈值，下一帧继续重试。 */
    RETRY,

    /** 连续失败达到阈值，或首帧即失败，会话必须结束。 */
    FAILED,
}

/** 帧循环的输出，决定 pump 是否继续。 */
sealed interface FrameOutcome {
    data object Continue : FrameOutcome
    data object Completed : FrameOutcome
    data class Failed(val error: ErrorCode) : FrameOutcome
}

/**
 * 帧来源：决定「多久推进一次」与「推进时做什么」。
 *
 * 单点与轨迹的差异只有这两点，抽成接口后 ticker 循环不再出现 `when(mode)`，
 * 也避免了两条分支各自维护一套暂停/取消逻辑。
 */
interface FrameSource {

    fun intervalMillis(): Long

    suspend fun onFrame(elapsedMillis: Long): FrameOutcome
}

/**
 * 模拟帧泵。
 *
 * 职责只有三件：按间隔唤醒、计算真实流逝时间、把结果交给 [FrameSource]。
 * 业务判断全部由帧来源与引擎完成，因此该循环可以被替换或暂停而不影响语义。
 *
 * 设计要点：
 * - **按实际时间差推进**：一次 GC 或卡顿后固定步长会让模拟位置落后于真实时间；
 * - 暂停时直接停止协程而不是空转轮询，恢复时重新计时，天然避免把暂停时长计入位移；
 * - 失败与完成都由帧来源返回，pump 不吞异常，也不自行改写会话状态。
 */
class SimulationPump(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val log: LogSink,
) {

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start(source: FrameSource, onFailure: (ErrorCode) -> Unit = {}) {
        stop()
        job = scope.launch {
            var lastTick = clock.elapsedMillis()
            while (isActive) {
                clock.delay(source.intervalMillis().coerceAtLeast(MIN_INTERVAL_MS))
                val now = clock.elapsedMillis()
                val elapsed = (now - lastTick).coerceAtLeast(1L)
                lastTick = now
                when (val outcome = source.onFrame(elapsed)) {
                    FrameOutcome.Continue -> Unit
                    FrameOutcome.Completed -> {
                        log.info { log.pumpCompleted }
                        return@launch
                    }
                    is FrameOutcome.Failed -> {
                        log.error { log.pumpStopped(outcome.error.code) }
                        onFailure(outcome.error)
                        return@launch
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        /** 最小间隔，防止误配置把主线程/电池拖垮。 */
        const val MIN_INTERVAL_MS = 100L
    }
}
