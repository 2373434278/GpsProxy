package com.gpsproxy.dev.core.i18n.text

import com.gpsproxy.dev.core.error.ErrorCode

/**
 * 错误码的三段式文案。
 *
 * 实现必须对 [ErrorCode] 做穷举 `when` 且不得写 `else`：
 * 新增错误码时编译器会强制补齐中英文。
 */
interface ErrorTexts {
    /** 拼接在 [action] 前的前缀，例如「建议：」。 */
    val suggestionPrefix: String
    fun title(code: ErrorCode): String
    fun cause(code: ErrorCode): String
    fun action(code: ErrorCode): String
}
