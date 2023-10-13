package io.embrace.android.embracesdk

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests for the Embrace SDK public API.
 */
internal class EmbraceTest {

    /**
     * Tests that the Embrace SDK instance is not null.
     */
    @Test
    fun instanceIsNotNull() {
        assertNotNull(Embrace.getInstance())
    }

    /**
     * Tests that the Embrace SDK returns a singleton and not a new object every time.
     */
    @Test
    fun instanceIsSameBetweenCalls() {
        val instance1 = Embrace.getInstance()
        val instance2 = Embrace.getInstance()
        assertSame(instance1, instance2)
    }
}
