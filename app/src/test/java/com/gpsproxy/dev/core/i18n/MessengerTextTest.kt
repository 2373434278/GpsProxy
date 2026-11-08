package com.gpsproxy.dev.core.i18n

import com.gpsproxy.dev.core.i18n.lang.EnStrings
import com.gpsproxy.dev.core.i18n.lang.ZhStrings
import com.gpsproxy.dev.core.messaging.AppMessenger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessengerTextTest {

    @Test
    fun `message text resolves at emit time with the current language`() = runTest {
        var current: Strings = ZhStrings
        val messenger = AppMessenger { current }
        val seen = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            messenger.messages.collect { seen += it.text }
        }

        messenger.show({ message.coordsCopied })
        current = EnStrings
        messenger.show({ message.coordsCopied })

        job.cancel()
        assertEquals(
            listOf(ZhStrings.message.coordsCopied, EnStrings.message.coordsCopied),
            seen,
        )
    }
}