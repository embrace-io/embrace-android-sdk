package io.embrace.android.embracesdk.internal.otel.state

import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.fixtures.fakeContextKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StateReplayMarkerTest {

    private val root = fakeOpenTelemetry(OtelSdkMode.REGULAR).context.root()

    @Test
    fun `a context marked as a state replay says it is one`() {
        assertTrue(root.markedAsStateReplay().isStateReplay())
    }

    @Test
    fun `the root context is not a state replay`() {
        assertFalse(root.isStateReplay())
    }

    @Test
    fun `a context carrying some other key is not a state replay`() {
        assertFalse(root.set(fakeContextKey, "value").isStateReplay())
    }

    @Test
    fun `marking a context leaves the root unmarked`() {
        root.markedAsStateReplay()
        assertFalse(root.isStateReplay())
    }
}
