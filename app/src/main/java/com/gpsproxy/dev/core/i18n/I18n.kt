package com.gpsproxy.dev.core.i18n

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 语言运行时。
 *
 * [strings] 供 Compose 订阅；[current] 是给非 Compose 调用方（通知、日志）的
 * 同步快照；[changes] 在语言真正变化时发出，用于刷新已创建的通知等一次性文案。
 */
class I18n(initial: AppLanguage = AppLanguage.SYSTEM) {

    private val _setting = MutableStateFlow(initial)
    private val _strings = MutableStateFlow(stringsFor(initial))
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    val strings: StateFlow<Strings> = _strings.asStateFlow()
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /** 用户当前的选择（可能是 [AppLanguage.SYSTEM]）。 */
    val setting: AppLanguage get() = _setting.value

    @Volatile
    var current: Strings = _strings.value
        private set

    @Synchronized
    fun apply(setting: AppLanguage) {
        _setting.value = setting
        val resolved = AppLanguage.resolve(setting)
        if (resolved == current.language) return
        val next = stringsFor(resolved)
        current = next
        _strings.value = next
        _changes.tryEmit(Unit)
    }

    /** 系统语言变化时重新解析 [AppLanguage.SYSTEM]。 */
    fun refreshSystemLocale() {
        if (_setting.value == AppLanguage.SYSTEM) apply(AppLanguage.SYSTEM)
    }
}
