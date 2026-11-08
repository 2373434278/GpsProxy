package com.gpsproxy.dev.core.i18n

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.i18n.lang.EnStrings
import com.gpsproxy.dev.core.i18n.lang.ZhStrings
import com.gpsproxy.dev.domain.container.LocationMode
import com.gpsproxy.dev.domain.place.PoiCategory
import com.gpsproxy.dev.domain.place.PoiSource
import com.gpsproxy.dev.domain.simulation.model.ChannelId
import com.gpsproxy.dev.domain.simulation.model.ChannelNoteKind
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.domain.simulation.model.TravelMode
import com.gpsproxy.dev.domain.simulation.route.RouteSourceKind
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 语言目录的完整性守卫。
 *
 * 文案本身不逐字断言（措辞会调整），但要求每个枚举取值都有非空译文，
 * 防止新增错误码/通道/路线类型时只补了中文或只补了英文。
 */
class StringsCatalogTest {

    private val catalogs = listOf(ZhStrings, EnStrings)

    private fun String.meaningful(key: String) = isNotBlank() && this != key

    @Test
    fun `every error code has title cause and action`() {
        catalogs.forEach { strings ->
            ErrorCode.entries.forEach { code ->
                assertTrue("${strings.language} title $code", strings.error.title(code).isNotBlank())
                assertTrue("${strings.language} cause $code", strings.error.cause(code).isNotBlank())
                assertTrue("${strings.language} action $code", strings.error.action(code).isNotBlank())
            }
        }
    }

    @Test
    fun `every channel has name description and note`() {
        catalogs.forEach { strings ->
            ChannelId.entries.forEach { id ->
                assertTrue("${strings.language} name $id", strings.channel.channelName(id.name).isNotBlank())
                assertTrue(
                    "${strings.language} description $id",
                    strings.channel.channelDescription(id.name).isNotBlank(),
                )
            }
            ChannelNoteKind.entries.forEach { kind ->
                assertTrue(
                    "${strings.language} note $kind",
                    strings.channel.note(kind.name, 1).isNotBlank(),
                )
            }
        }
    }

    @Test
    fun `route enum labels are translated`() {
        catalogs.forEach { strings ->
            RouteKind.entries.forEach {
                assertTrue(
                    "${strings.language} route kind $it",
                    strings.route.routeKindLabel(it.name).isNotBlank(),
                )
            }
            TravelMode.entries.forEach {
                assertTrue(
                    "${strings.language} travel mode $it",
                    strings.route.travelModeLabel(it.name).isNotBlank(),
                )
            }
            RouteSourceKind.entries.forEach {
                assertTrue(
                    "${strings.language} route source $it",
                    strings.route.routeSourceLabel(it.name).isNotBlank(),
                )
            }
        }
    }

    @Test
    fun `place and container enum labels are translated`() {
        catalogs.forEach { strings ->
            PoiCategory.entries.forEach {
                assertTrue(
                    "${strings.language} poi category $it",
                    strings.place.poiCategoryLabel(it.name).meaningful(it.name),
                )
            }
            PoiSource.entries.forEach {
                assertTrue(
                    "${strings.language} poi source $it",
                    strings.place.poiSourceLabel(it.name).meaningful(it.name),
                )
            }
            LocationMode.entries.forEach {
                assertTrue(
                    "${strings.language} location mode $it",
                    strings.container.locationModeLabel(it.name).meaningful(it.name),
                )
            }
        }
    }

    @Test
    fun `chinese and english catalogs actually differ`() {
        assertNotEquals(ZhStrings.home.startSimulation, EnStrings.home.startSimulation)
        assertNotEquals(ZhStrings.settings.languageTitle, EnStrings.settings.languageTitle)
        assertNotEquals(ZhStrings.notification.channelName, EnStrings.notification.channelName)
        assertNotEquals(ZhStrings.selfCheck.engineVersionTitle, EnStrings.selfCheck.engineVersionTitle)
        assertNotEquals(ZhStrings.onboarding.appTitle, EnStrings.onboarding.appTitle)
        assertNotEquals(ZhStrings.log.paused, EnStrings.log.paused)
    }

    @Test
    fun `resolved catalogs follow the language setting`() {
        assertTrue(stringsFor(AppLanguage.ZH_HANS) === ZhStrings)
        assertTrue(stringsFor(AppLanguage.EN) === EnStrings)
    }
}