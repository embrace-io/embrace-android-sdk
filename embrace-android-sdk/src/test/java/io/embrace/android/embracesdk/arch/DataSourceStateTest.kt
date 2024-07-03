package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.fakes.system.mockContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

internal class DataSourceStateTest {

    private val ctx = mockContext()

    @Test
    fun `null session type is never enabled`() {
        val source = FakeDataSource(ctx)
        val state = DataSourceState(
            factory = { source },
            configGate = { true }
        )

        // data source not retrievable if not session type is null
        assertNull(state.dataSource)

        // data capture is enabled by default.
        state.onConfigChange()
        state.currentSessionType = null
        assertEquals(0, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)

        // data capture enabled for a session
        state.currentSessionType = SessionType.FOREGROUND
        assertSame(source, state.dataSource)
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)

        // data capture disabled for no session
        state.currentSessionType = null
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(1, source.disableDataCaptureCount)

        // functions can be called multiple times without issue
        state.currentSessionType = SessionType.FOREGROUND
        state.currentSessionType = null
        assertEquals(2, source.enableDataCaptureCount)
        assertEquals(2, source.disableDataCaptureCount)
    }

    @Test
    fun `test config gate defaults to enabled`() {
        val source = FakeDataSource(ctx)
        DataSourceState(
            factory = { source },
        ).apply {
            currentSessionType = SessionType.FOREGROUND
        }

        // data capture is enabled by default.
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)
        assertEquals(1, source.resetCount)
    }

    @Test
    fun `test config gate enabled by default`() {
        val source = FakeDataSource(ctx)
        DataSourceState(
            factory = { source },
            configGate = { true },
        ).apply {
            currentSessionType = SessionType.FOREGROUND
        }

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
        ).apply {
            currentSessionType = SessionType.FOREGROUND
        }

        // data source not retrievable if disabled
        assertNull(state.dataSource)

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
            disabledSessionType = SessionType.BACKGROUND
        ).apply {
            currentSessionType = SessionType.BACKGROUND
        }

        // data capture is always disabled by default.
        assertEquals(0, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)
        assertEquals(1, source.resetCount)

        // new session should enable data capture
        state.currentSessionType = SessionType.FOREGROUND
        state.currentSessionType = SessionType.FOREGROUND
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)
        assertEquals(3, source.resetCount)

        // extra payload types should not re-register listeners
        state.currentSessionType = SessionType.BACKGROUND
        state.currentSessionType = SessionType.BACKGROUND
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(1, source.disableDataCaptureCount)
        assertEquals(5, source.resetCount)

        // functions can be called multiple times without issue
        state.currentSessionType = SessionType.FOREGROUND
        state.currentSessionType = SessionType.BACKGROUND
        assertEquals(2, source.enableDataCaptureCount)
        assertEquals(2, source.disableDataCaptureCount)
        assertEquals(7, source.resetCount)
    }
}
