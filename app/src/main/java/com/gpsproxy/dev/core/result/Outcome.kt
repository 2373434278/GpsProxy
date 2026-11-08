package com.gpsproxy.dev.core.result

import com.gpsproxy.dev.core.error.ErrorCode

/**
 * 领域错误的统一载体。
 *
 * 与 `kotlin.Result` 的区别：失败分支是**结构化**的（错误码 + 原因 + 细节），
 * 调用方不需要再从 `state.errorCode` 这种旁路字段里"猜"发生了什么，
 * UI 也能直接拿到可展示的标题与操作建议。
 *
 * @param code 目录中的错误码，决定标题与引导文案
 * @param cause 面向日志的内部原因，不直接展示给用户
 * @param detail 可选补充信息（例如降级到的通道名），可辅助排查
 * @param retryable 同一操作重试是否可能成功（用于决定是否展示"重试"按钮）
 */
data class DomainError(
    val code: ErrorCode,
    val cause: String,
    val detail: String? = null,
    val retryable: Boolean = false,
) {
    /** 写入日志的一行摘要。 */
    val logLine: String get() = "${code.code} · $cause"
}

/**
 * 领域层统一返回值。
 *
 * 用密封类型而不是抛异常：注入失败、容器不可用这些都是**预期分支**，
 * 必须被调用方显式处理；同时避免 `Result` 包装不可控的异常类型。
 */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: DomainError) : Outcome<Nothing>
}

val Outcome<*>.isSuccess: Boolean get() = this is Outcome.Success
val Outcome<*>.isFailure: Boolean get() = this is Outcome.Failure

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

fun Outcome<*>.errorOrNull(): DomainError? = (this as? Outcome.Failure)?.error

inline fun <T> Outcome<T>.onSuccess(block: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) block(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(block: (DomainError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) block(error)
    return this
}

fun <T> Outcome<T>.getOrElse(fallback: (DomainError) -> T): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> fallback(error)
}

fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/** 构造成功结果，语义等价于 `Outcome.Success(Unit)`。 */
fun success(): Outcome<Unit> = Outcome.Success(Unit)

/** 构造失败结果，便于在领域层用一行表达错误码与原因。 */
fun <T> failure(code: ErrorCode, cause: String, detail: String? = null): Outcome<T> =
    Outcome.Failure(DomainError(code = code, cause = cause, detail = detail))
