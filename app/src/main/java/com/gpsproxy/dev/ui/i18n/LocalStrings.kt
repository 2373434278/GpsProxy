package com.gpsproxy.dev.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.gpsproxy.dev.core.i18n.AppLanguage
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.stringsFor

/**
 * Compose 侧的文案入口。
 *
 * 语言切换很少发生，用 `staticCompositionLocalOf` 让整个界面一次性重组即可；
 * 默认值按系统语言解析，保证任何独立预览/测试组合都不为 null。
 */
val LocalStrings = staticCompositionLocalOf<Strings> {
    stringsFor(AppLanguage.resolve(AppLanguage.SYSTEM))
}
