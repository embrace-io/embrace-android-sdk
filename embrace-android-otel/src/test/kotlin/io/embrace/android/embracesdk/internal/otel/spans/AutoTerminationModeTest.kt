package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.spans.AutoTerminationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

internal class AutoTerminationModeTest {

    @Test
    fun `timeout uses value equality`() {
        assertEquals(AutoTerminationMode.Timeout(1000L), AutoTerminationMode.Timeout(1000L))
        assertEquals(AutoTerminationMode.Timeout(1000L).hashCode(), AutoTerminationMode.Timeout(1000L).hashCode())
        assertNotEquals(AutoTerminationMode.Timeout(1000L), AutoTerminationMode.Timeout(2000L))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `deprecated companion values alias the new cases`() {
        assertSame(AutoTerminationMode.None, AutoTerminationMode.NONE)
        assertSame(AutoTerminationMode.OnBackground, AutoTerminationMode.ON_BACKGROUND)
    }
}
