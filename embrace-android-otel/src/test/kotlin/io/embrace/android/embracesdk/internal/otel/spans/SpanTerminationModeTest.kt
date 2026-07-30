package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.spans.AutoTerminationMode
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanTerminationModeTest {

    @Test
    fun `public auto termination mode maps to internal equivalent`() {
        assertEquals(SpanTerminationMode.None, AutoTerminationMode.NONE.toTerminationMode())
        assertEquals(SpanTerminationMode.OnBackground, AutoTerminationMode.ON_BACKGROUND.toTerminationMode())
    }

    @Test
    fun `internal termination mode maps back to public equivalent`() {
        assertEquals(AutoTerminationMode.NONE, SpanTerminationMode.None.toAutoTerminationMode())
        assertEquals(AutoTerminationMode.ON_BACKGROUND, SpanTerminationMode.OnBackground.toAutoTerminationMode())
        assertEquals(AutoTerminationMode.NONE, SpanTerminationMode.Timeout(1000L).toAutoTerminationMode())
    }
}
