package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.injection.Override
import io.embrace.android.embracesdk.internal.injection.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class OverrideTest {

    private var realCalls = 0

    @Before
    fun setUp() {
        realCalls = 0
    }

    @Test
    fun `null override uses real construction`() {
        val override: Override<String>? = null
        assertEquals("real", override.resolve(::real))
        assertEquals(1, realCalls)
    }

    @Test
    fun `replace override skips real construction`() {
        val override: Override<String> = { "fake" }
        assertEquals("fake", override.resolve(::real))
        assertEquals(0, realCalls)
    }

    @Test
    fun `wrap override builds real construction once`() {
        val override: Override<String> = { "wrapped-${it()}" }
        assertEquals("wrapped-real", override.resolve(::real))
        assertEquals(1, realCalls)
    }

    @Test
    fun `failing override propagates without real construction`() {
        val failure = IllegalStateException()
        val override: Override<String> = { throw failure }
        val thrown = runCatching { override.resolve(::real) }.exceptionOrNull()
        assertSame(failure, thrown)
        assertEquals(0, realCalls)
    }

    private fun real(): String {
        realCalls++
        return "real"
    }
}
