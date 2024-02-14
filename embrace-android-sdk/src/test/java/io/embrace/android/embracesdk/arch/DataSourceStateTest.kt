package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.fakes.system.mockContext
import org.junit.Assert.assertEquals
import org.junit.Test

internal class DataSourceStateTest {

    private val ctx = mockContext()

    @Test
    fun `null session type is never enabled`() {
        val source = FakeDataSource(ctx)
        val state = DataSourceState(
            factory = { source },
            configGate = { true },
            currentSessionType = null
        )

        // data capture is enabled by default.
        state.onConfigChange()
        state.onSessionTypeChange(null)
        assertEquals(0, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)

        // data capture enabled for a session
        state.onSessionTypeChange(SessionType.FOREGROUND)
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)

        // data capture disabled for no session
        state.onSessionTypeChange(null)
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(1, source.disableDataCaptureCount)

        // functions can be called multiple times without issue
        state.onSessionTypeChange(SessionType.FOREGROUND)
        state.onSessionTypeChange(null)
        assertEquals(2, source.enableDataCaptureCount)
        assertEquals(2, source.disableDataCaptureCount)
    }

    @Test
    fun `test config gate defaults to enabled`() {
        val source = FakeDataSource(ctx)
        DataSourceState(
            factory = { source },
            currentSessionType = SessionType.FOREGROUND
        )

        // data capture is enabled by default.
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)
    }

    @Test
    fun `test config gate enabled by default`() {
        val source = FakeDataSource(ctx)
        DataSourceState(
            factory = { source },
            configGate = { true },
            currentSessionType = SessionType.FOREGROUND
        )

        // data capture is enabled by default.
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)
    }

    @Test
    fun `test config gate affects data capture`() {
        val source = FakeDataSource(ctx)
        var enabled = false
        val state = DataSourceState(
            factory = { source },
            configGate = { enabled },
            currentSessionType = SessionType.FOREGROUND
        )

        // data capture is disabled by default.
        assertEquals(0, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)

        // enabling the config gate should enable data capture
        enabled = true
        state.onConfigChange()
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)

        // another config change should not reregister listeners
        state.onConfigChange()
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)

        // deregistering works
        enabled = false
        state.onConfigChange()
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(1, source.disableDataCaptureCount)

        // functions can be called multiple times without issue
        enabled = true
        state.onConfigChange()
        enabled = false
        state.onConfigChange()
        assertEquals(2, source.enableDataCaptureCount)
        assertEquals(2, source.disableDataCaptureCount)
    }

    @Test
    fun `test session type affects data capture`() {
        val source = FakeDataSource(ctx)
        val state = DataSourceState(
            factory = { source },
            configGate = { true },
            SessionType.BACKGROUND,
            disabledSessionType = SessionType.BACKGROUND
        )

        // data capture is always disabled by default.
        assertEquals(0, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)

        // new session should enable data capture
        state.onSessionTypeChange(SessionType.FOREGROUND)
        state.onSessionTypeChange(SessionType.FOREGROUND)
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)

        // extra payload types should not re-register listeners
        state.onSessionTypeChange(SessionType.BACKGROUND)
        state.onSessionTypeChange(SessionType.BACKGROUND)
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(1, source.disableDataCaptureCount)

        // functions can be called multiple times without issue
        state.onSessionTypeChange(SessionType.FOREGROUND)
        state.onSessionTypeChange(SessionType.BACKGROUND)
        assertEquals(2, source.enableDataCaptureCount)
        assertEquals(2, source.disableDataCaptureCount)
    }
}
