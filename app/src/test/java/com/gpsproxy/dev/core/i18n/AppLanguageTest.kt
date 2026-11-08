package com.gpsproxy.dev.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    @Test
    fun `explicit selection ignores system locale`() {
        assertEquals(AppLanguage.EN, AppLanguage.resolve(AppLanguage.EN, Locale.CHINA))
        assertEquals(AppLanguage.ZH_HANS, AppLanguage.resolve(AppLanguage.ZH_HANS, Locale.US))
    }

    @Test
    fun `system follows chinese locales`() {
        assertEquals(AppLanguage.ZH_HANS, AppLanguage.resolve(AppLanguage.SYSTEM, Locale.CHINA))
        assertEquals(AppLanguage.ZH_HANS, AppLanguage.resolve(AppLanguage.SYSTEM, Locale.TAIWAN))
        assertEquals(AppLanguage.ZH_HANS, AppLanguage.resolve(AppLanguage.SYSTEM, Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `system maps other locales to english`() {
        assertEquals(AppLanguage.EN, AppLanguage.resolve(AppLanguage.SYSTEM, Locale.US))
        assertEquals(AppLanguage.EN, AppLanguage.resolve(AppLanguage.SYSTEM, Locale.JAPAN))
        assertEquals(AppLanguage.EN, AppLanguage.resolve(AppLanguage.SYSTEM, Locale.GERMANY))
    }

    @Test
    fun `unknown locale falls back to chinese`() {
        assertEquals(AppLanguage.ZH_HANS, AppLanguage.resolve(AppLanguage.SYSTEM, Locale.ROOT))
    }
}
