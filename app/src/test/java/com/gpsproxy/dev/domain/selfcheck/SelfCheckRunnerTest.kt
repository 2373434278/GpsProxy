package com.gpsproxy.dev.domain.selfcheck

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SelfCheckRunnerTest {

    @Test
    fun `checks run in order and publish results`() = runTest {
        val order = mutableListOf<String>()
        val runner = SelfCheckRunner(
            scope = backgroundScope,
            definitions = {
                listOf(
                    SelfCheckDefinition("A", "pending") {
                        order += "A"
                        SelfCheckResult(true, "ok")
                    },
                    SelfCheckDefinition("B", "pending") {
                        order += "B"
                        SelfCheckResult(false, "bad")
                    },
                )
            },
        )
        runner.run()
        runCurrent()

        assertEquals(listOf("A", "B"), order)
        assertEquals(listOf(StepState.DONE, StepState.WARN), runner.steps.value.map { it.state })
        assertEquals(false, runner.allPassed.value)
        assertFalse(runner.running.value)
    }

    @Test
    fun `finish callback receives the aggregated result`() = runTest {
        var finished: Boolean? = null
        val runner = SelfCheckRunner(
            scope = backgroundScope,
            definitions = {
                listOf(SelfCheckDefinition("only", "pending") { SelfCheckResult(true, "ok") })
            },
            onFinished = { finished = it },
        )
        runner.run()
        runCurrent()
        assertEquals(true, finished)
    }

    @Test
    fun `runner ignores a second run while in progress`() = runTest {
        var invocations = 0
        val runner = SelfCheckRunner(
            scope = backgroundScope,
            definitions = {
                listOf(
                    SelfCheckDefinition("slow", "pending") {
                        invocations++
                        SelfCheckResult(true, "ok")
                    },
                )
            },
        )
        runner.run()
        runner.run()
        runCurrent()
        assertEquals(1, invocations)
    }

    @Test
    fun `throwing check degrades to a warning instead of crashing`() = runTest {
        val runner = SelfCheckRunner(
            scope = backgroundScope,
            definitions = {
                listOf(SelfCheckDefinition("boom", "pending") { error("kaboom") })
            },
        )
        runner.run()
        runCurrent()
        assertEquals(StepState.WARN, runner.steps.value.single().state)
        assertTrue(runner.steps.value.single().detail.contains("IllegalStateException"))
    }
}
