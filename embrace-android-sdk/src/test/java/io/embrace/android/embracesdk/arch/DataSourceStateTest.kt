package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.fakes.FakeDataSource
import org.junit.Assert.assertEquals
import org.junit.Test

internal class DataSourceStateTest {

    @Test
    fun `null envelope is never enabled`() {
        val source = FakeDataSource()
        val state = DataSourceState(
            factory = { source },
            configGate = { true },
            currentEnvelope = null
        )

        // data capture is enabled by default.
        state.onConfigChange()
        state.onEnvelopeTypeChange(null)
        assertEquals(0, source.registerCount)
        assertEquals(0, source.unregisterCount)

        // data capture enabled for an envelope
        state.onEnvelopeTypeChange(EnvelopeType.SESSION)
        assertEquals(1, source.registerCount)
        assertEquals(0, source.unregisterCount)

        // data capture disabled for no envelope
        state.onEnvelopeTypeChange(null)
        assertEquals(1, source.registerCount)
        assertEquals(1, source.unregisterCount)

        // functions can be called multiple times without issue
        state.onEnvelopeTypeChange(EnvelopeType.SESSION)
        state.onEnvelopeTypeChange(null)
        assertEquals(2, source.registerCount)
        assertEquals(2, source.unregisterCount)
    }

    @Test
    fun `test config gate enabled by default`() {
        val source = FakeDataSource()
        DataSourceState(
            factory = { source },
            configGate = { true },
            currentEnvelope = EnvelopeType.SESSION
        )

        // data capture is enabled by default.
        assertEquals(1, source.registerCount)
        assertEquals(0, source.unregisterCount)
    }

    @Test
    fun `test config gate affects data capture`() {
        val source = FakeDataSource()
        var enabled = false
        val state = DataSourceState(
            factory = { source },
            configGate = { enabled },
            currentEnvelope = EnvelopeType.SESSION
        )

        // data capture is disabled by default.
        assertEquals(0, source.registerCount)
        assertEquals(0, source.unregisterCount)

        // enabling the config gate should enable data capture
        enabled = true
        state.onConfigChange()
        assertEquals(1, source.registerCount)
        assertEquals(0, source.unregisterCount)

        // another config change should not reregister listeners
        state.onConfigChange()
        assertEquals(1, source.registerCount)
        assertEquals(0, source.unregisterCount)

        // deregistering works
        enabled = false
        state.onConfigChange()
        assertEquals(1, source.registerCount)
        assertEquals(1, source.unregisterCount)

        // functions can be called multiple times without issue
        enabled = true
        state.onConfigChange()
        enabled = false
        state.onConfigChange()
        assertEquals(2, source.registerCount)
        assertEquals(2, source.unregisterCount)
    }

    @Test
    fun `test envelope type affects data capture`() {
        val source = FakeDataSource()
        val state = DataSourceState(
            factory = { source },
            configGate = { true },
            EnvelopeType.BACKGROUND_ACTIVITY,
            disabledEnvelopeType = EnvelopeType.BACKGROUND_ACTIVITY
        )

        // data capture is always disabled by default.
        assertEquals(0, source.registerCount)
        assertEquals(0, source.unregisterCount)

        // new session should enable data capture
        state.onEnvelopeTypeChange(EnvelopeType.SESSION)
        state.onEnvelopeTypeChange(EnvelopeType.SESSION)
        assertEquals(1, source.registerCount)
        assertEquals(0, source.unregisterCount)

        // extra payload types should not re-register listeners
        state.onEnvelopeTypeChange(EnvelopeType.BACKGROUND_ACTIVITY)
        state.onEnvelopeTypeChange(EnvelopeType.BACKGROUND_ACTIVITY)
        assertEquals(1, source.registerCount)
        assertEquals(1, source.unregisterCount)

        // functions can be called multiple times without issue
        state.onEnvelopeTypeChange(EnvelopeType.SESSION)
        state.onEnvelopeTypeChange(EnvelopeType.BACKGROUND_ACTIVITY)
        assertEquals(2, source.registerCount)
        assertEquals(2, source.unregisterCount)
    }
}
