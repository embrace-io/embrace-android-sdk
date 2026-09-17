package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.internal.api.SdkApi
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DeviceScenarioScopeTest {

    private val cycles = mutableListOf<Long>()
    private val sdk = mockk<SdkApi>(relaxed = true)
    private val scope = DeviceScenarioScope(sdk, cycles::add)

    @Test
    fun `the scenario is given the sdk under test`() {
        assertSame(sdk, scope.embrace)
    }

    @Test
    fun `the clock is the device's`() {
        val before = System.currentTimeMillis()
        val observed = scope.nowMs
        assertTrue("$observed is not between $before and now", observed in before..System.currentTimeMillis())
    }

    @Test
    fun `advancing time waits for real`() {
        val elapsed = timed { scope.advanceTime(WAIT_MS) }
        assertTrue("waited ${elapsed}ms", elapsed >= WAIT_MS)
    }

    @Test
    fun `backgrounding delegates to the cycler rather than waiting`() {
        val elapsed = timed { scope.backgroundAndReturn(WAIT_MS) }
        assertEquals(listOf(WAIT_MS), cycles)
        assertTrue("waited ${elapsed}ms itself", elapsed < WAIT_MS)
    }

    private fun timed(action: () -> Unit): Long {
        val start = System.nanoTime()
        action()
        return (System.nanoTime() - start) / 1_000_000
    }

    private companion object {
        const val WAIT_MS = 50L
    }
}
