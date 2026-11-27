package io.embrace.android.embracesdk.internal.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StateValuesTest {

    @Test
    fun `a value of exactly the limit is left alone`() {
        val value = "a".repeat(2_000)
        assertEquals(value, StateValues.cap(value))
    }

    @Test
    fun `a value one past the limit is cut to 1997 characters and an ellipsis`() {
        val capped = StateValues.cap("a".repeat(2_001))
        assertEquals("a".repeat(1_997) + "...", capped)
        assertEquals(2_000, capped.length)
    }

    @Test
    fun `an update is a repeat only when both the text and the system flag are the same`() {
        assertTrue(StateValues.isRepeat("wifi", false, "wifi", false))
        assertTrue(StateValues.isRepeat("wifi", true, "wifi", true))
        assertFalse(StateValues.isRepeat("wifi", false, "wifi", true))
        assertFalse(StateValues.isRepeat("wifi", true, "wifi", false))
        assertFalse(StateValues.isRepeat("wifi", false, "none", false))
        assertFalse(StateValues.isRepeat("wifi", true, "none", true))
        assertFalse(StateValues.isRepeat("wifi", false, "none", true))
        assertFalse(StateValues.isRepeat("wifi", true, "none", false))
    }
}
