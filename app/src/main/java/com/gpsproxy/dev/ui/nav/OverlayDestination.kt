package com.gpsproxy.dev.ui.nav

/**
 * 悬浮球/外部入口携带的目的地。
 *
 * 用枚举替代裸字符串：旧实现的 `when (route) { "favorites" -> ... }` 拼错时
 * 不会编译失败，只会静默不跳转；`UNKNOWN` 也显式建模，避免把未知输入
 * 当成某个默认页面。
 */
enum class OverlayDestination(val raw: String) {
    FAVORITES("favorites"),
    PICK("pick"),
    APPS("apps"),
    ROUTE("route"),
    UNKNOWN("");

    companion object {
        fun parse(raw: String?): OverlayDestination =
            entries.firstOrNull { it.raw == raw && it != UNKNOWN } ?: UNKNOWN
    }
}
