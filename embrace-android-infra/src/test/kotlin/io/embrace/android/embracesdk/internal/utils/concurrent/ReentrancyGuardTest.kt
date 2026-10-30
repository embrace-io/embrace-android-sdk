package io.embrace.android.embracesdk.internal.utils.concurrent

import org.junit.Assert.assertEquals
import org.junit.Test

internal class ReentrancyGuardTest {

    private val guard = ReentrancyGuard()

    @Test
    fun `a nested call on the same thread gets the reentry result`() {
        val result = guard.runExclusive(onReentry = { "outer reentry" }) {
            guard.runExclusive(onReentry = { "nested reentry" }) { "nested block" }
        }

        assertEquals("nested reentry", result)
    }

    @Test
    fun `the guard is released after the block returns or throws`() {
        runCatching { guard.runExclusive(onReentry = { "reentry" }) { error("failed") } }

        assertEquals("block", guard.runExclusive(onReentry = { "reentry" }) { "block" })
    }
}
