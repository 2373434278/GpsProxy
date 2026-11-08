package com.gpsproxy.dev.domain.selfcheck

/**
 * 自检步骤的展示状态。
 *
 * 定义在领域层而不是 UI 层：自检的执行顺序与结果来自平台探测，
 * UI 只负责把状态画成徽标。
 */
enum class StepState { PENDING, RUNNING, DONE, WARN }

/**
 * 一条自检结果。
 *
 * @param title 步骤名
 * @param detail 结果说明（成功时说明现状，失败时说明原因与下一步）
 */
data class SelfCheckStep(
    val title: String,
    val detail: String,
    val state: StepState = StepState.PENDING,
)
