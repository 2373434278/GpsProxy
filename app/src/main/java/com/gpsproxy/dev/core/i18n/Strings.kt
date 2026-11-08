package com.gpsproxy.dev.core.i18n

import com.gpsproxy.dev.core.i18n.lang.EnStrings
import com.gpsproxy.dev.core.i18n.lang.ZhStrings
import com.gpsproxy.dev.core.i18n.text.ChannelTexts
import com.gpsproxy.dev.core.i18n.text.CommonTexts
import com.gpsproxy.dev.core.i18n.text.ContainerTexts
import com.gpsproxy.dev.core.i18n.text.ErrorTexts
import com.gpsproxy.dev.core.i18n.text.HomeTexts
import com.gpsproxy.dev.core.i18n.text.LogTexts
import com.gpsproxy.dev.core.i18n.text.MessageTexts
import com.gpsproxy.dev.core.i18n.text.NotificationTexts
import com.gpsproxy.dev.core.i18n.text.OnboardingTexts
import com.gpsproxy.dev.core.i18n.text.PlaceTexts
import com.gpsproxy.dev.core.i18n.text.RouteTexts
import com.gpsproxy.dev.core.i18n.text.SelfCheckTexts
import com.gpsproxy.dev.core.i18n.text.SettingsTexts

/**
 * 全部用户可见文案的根接口。
 *
 * 按功能分组而不是一个大接口：每个功能域可以独立迁移、独立评审，
 * 新增文案时只改对应分组，不会把几十个页面卷进同一个 diff。
 *
 * 领域层不要直接持有已经渲染好的字符串，而应暴露语义枚举，
 * 由 UI/通知/日志在边界上通过本接口解析。
 */
interface Strings {
    val language: AppLanguage
    val common: CommonTexts
    val home: HomeTexts
    val settings: SettingsTexts
    val route: RouteTexts
    val error: ErrorTexts
    val selfCheck: SelfCheckTexts
    val channel: ChannelTexts
    val message: MessageTexts
    val place: PlaceTexts
    val container: ContainerTexts
    val onboarding: OnboardingTexts
    val notification: NotificationTexts
    val log: LogTexts
}

/** [AppLanguage.SYSTEM] 会按设备语言解析；未知语言回退中文。 */
fun stringsFor(language: AppLanguage): Strings =
    if (AppLanguage.resolve(language) == AppLanguage.EN) EnStrings else ZhStrings
