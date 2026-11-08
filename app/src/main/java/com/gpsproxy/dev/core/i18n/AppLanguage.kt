package com.gpsproxy.dev.core.i18n

import java.util.Locale

/**
 * 界面语言的用户选择。
 *
 * [SYSTEM] 表示跟随系统；解析规则集中在 [resolve]，避免各处重复判断。
 */
enum class AppLanguage {
    SYSTEM,
    ZH_HANS,
    EN;

    companion object {

        /**
         * 显式选择优先；[SYSTEM] 时中文（含 zh-TW 等）→ 中文，
         * 未知/空白 locale → 中文（产品默认），其余 → 英文。
         */
        fun resolve(setting: AppLanguage, locale: Locale = Locale.getDefault()): AppLanguage {
            if (setting != SYSTEM) return setting
            val code = locale.language.lowercase(Locale.ROOT)
            return if (code.isBlank() || code == "und" || code == "zh") ZH_HANS else EN
        }
    }
}
