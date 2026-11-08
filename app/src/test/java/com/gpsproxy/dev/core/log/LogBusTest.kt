package com.gpsproxy.dev.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogBusTest {

    @Test
    fun `ring buffer keeps only the configured capacity`() {
        val sink = InMemoryLogSink(capacity = 3, renderLimit = 2, timestamp = { 1L })
        repeat(5) { sink.info("m$it") }
        assertEquals(3, sink.snapshot().size)
        assertEquals(listOf("m3", "m4"), sink.entries.value.map { it.message })
    }

    @Test
    fun `severity is preserved and forwarded to the writer`() {
        val written = mutableListOf<LogSeverity>()
        val sink = InMemoryLogSink(writer = { written += it.severity })
        sink.info("i")
        sink.warn("w")
        sink.error("e")
        assertEquals(listOf(LogSeverity.INFO, LogSeverity.WARN, LogSeverity.ERROR), written)
        assertEquals(3, sink.snapshot().size)
    }

    @Test
    fun `clear empties both views`() {
        val sink = InMemoryLogSink()
        sink.info("a")
        sink.clear()
        assertTrue(sink.snapshot().isEmpty())
        assertTrue(sink.entries.value.isEmpty())
    }

    @Test
    fun `time text is formatted from the injected timestamp`() {
        val sink = InMemoryLogSink(timestamp = { 0L })
        sink.info("epoch")
        assertTrue(sink.snapshot().first().timeText().matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
    }
}
