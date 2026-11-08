package com.gpsproxy.dev.domain.selfcheck

import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.StringsHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 单项检查结果。 */
data class SelfCheckResult(val passed: Boolean, val detail: String)

/** 一项自检：标题 + 实际探测逻辑。 */
data class SelfCheckDefinition(
    val title: String,
    val pendingDetail: String,
    val check: () -> SelfCheckResult,
)

/**
 * 自检执行器。
 *
 * 旧实现用 `delay(430)` / `delay(260)` 人为拖长进度，让"检测"看起来在工作：
 * 真实的 Binder 探测是毫秒级的，用户实际等了 2.5 秒什么也没发生。
 * 现在只执行真实检查并逐项发布结果，界面可以在结果到达时做位移动画，
 * 而不是让业务逻辑假装耗时。
 *
 * 运行在应用级协程作用域：用户离开页面时自检继续，回来能看到最终结果。
 */
class SelfCheckRunner(
    private val scope: CoroutineScope,
    /** 每次运行时重建定义：步骤文案按当前语言解析，切换语言后重跑即可更新。 */
    private val definitions: () -> List<SelfCheckDefinition>,
    private val onFinished: (Boolean) -> Unit = {},
    private val strings: () -> Strings = { StringsHolder.current },
) {

    private val _steps = MutableStateFlow<List<SelfCheckStep>>(emptyList())
    val steps: StateFlow<List<SelfCheckStep>> = _steps.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** null 表示尚未完成过一次完整自检。 */
    private val _allPassed = MutableStateFlow<Boolean?>(null)
    val allPassed: StateFlow<Boolean?> = _allPassed.asStateFlow()

    fun run() {
        if (_running.value) return
        _running.value = true
        val current = definitions()
        _steps.value = current.map {
            SelfCheckStep(title = it.title, detail = it.pendingDetail)
        }
        scope.launch {
            var allPassed = true
            current.forEachIndexed { index, definition ->
                markStep(index, StepState.RUNNING, definition.pendingDetail)
                val result = runCatching { definition.check() }
                    .getOrElse {
                        SelfCheckResult(
                            false,
                            strings().selfCheck.exception(it.javaClass.simpleName),
                        )
                    }
                if (!result.passed) allPassed = false
                markStep(
                    index = index,
                    state = if (result.passed) StepState.DONE else StepState.WARN,
                    detail = result.detail,
                )
            }
            _allPassed.value = allPassed
            _running.value = false
            onFinished(allPassed)
        }
    }

    private fun markStep(index: Int, state: StepState, detail: String) {
        _steps.update { list ->
            list.mapIndexed { current, step ->
                if (current == index) step.copy(state = state, detail = detail) else step
            }
        }
    }
}
