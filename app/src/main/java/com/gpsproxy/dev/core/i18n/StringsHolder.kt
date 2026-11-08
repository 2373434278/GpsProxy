package com.gpsproxy.dev.core.i18n

/**
 * 进程级字符串提供者。
 *
 * 只给 [com.gpsproxy.dev.core.log.LogBus] 这类必须保持 object 单例的组件使用；
 * 其余代码通过构造参数、ViewModel 或 CompositionLocal 获取。
 */
object StringsHolder {

    @Volatile
    private var provider: (() -> Strings)? = null

    private val fallback: Strings = stringsFor(AppLanguage.resolve(AppLanguage.SYSTEM))

    fun install(provider: () -> Strings) {
        this.provider = provider
    }

    val current: Strings
        get() = provider?.invoke() ?: fallback

    fun text(block: Strings.() -> String): String = current.block()
}
