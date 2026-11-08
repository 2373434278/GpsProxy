package com.gpsproxy.dev.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class I18nTest {

    @Test
    fun `apply switches the resolved catalog`() {
        val i18n = I18n(AppLanguage.ZH_HANS)
        i18n.apply(AppLanguage.EN)

        assertEquals(AppLanguage.EN, i18n.setting)
        assertEquals(AppLanguage.EN, i18n.current.language)
        assertEquals(AppLanguage.EN, i18n.strings.value.language)
    }

    @Test
    fun `reapplying the same language keeps the same instance`() {
        val i18n = I18n(AppLanguage.ZH_HANS)
        val before = i18n.current
        i18n.apply(AppLanguage.ZH_HANS)
        assertSame(before, i18n.current)
    }

    @Test
    fun `system setting resolves to a concrete catalog`() {
        val i18n = I18n(AppLanguage.SYSTEM)
        assertEquals(
            AppLanguage.resolve(AppLanguage.SYSTEM),
            i18n.current.language,
        )
    }
}
