package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.spans.AutoTerminationMode
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanTerminationModeTest {

    @Test
    fun `public auto termination mode maps to internal equivalent`() {
        assertEquals(SpanTerminationMode.None, AutoTerminationMode.None.toTerminationMode())
        assertEquals(SpanTerminationMode.OnBackground, AutoTerminationMode.OnBackground.toTerminationMode())
    }

    @Test
    fun `internal termination mode maps back to public equivalent`() {
        assertEquals(AutoTerminationMode.None, SpanTerminationMode.None.toAutoTerminationMode())
        assertEquals(AutoTerminationMode.OnBackground, SpanTerminationMode.OnBackground.toAutoTerminationMode())
        assertEquals(AutoTerminationMode.None, SpanTerminationMode.Timeout(1000L).toAutoTerminationMode())
    }
}
