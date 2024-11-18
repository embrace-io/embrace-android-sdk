package io.embrace.android.embracesdk.internal.arch

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class DataSourceStateTest {

    @Test
    fun `null session type is never enabled`() {
        val source = FakeDataSource(RuntimeEnvironment.getApplication())
        val state = DataSourceState(
            factory = { source },
            configGate = { true }
        )

        // data source not retrievable if the session type is null
        assertNull(state.dataSource)

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
        val source = FakeDataSource(RuntimeEnvironment.getApplication())
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
    fun `test config gate enabled`() {
        val source = FakeDataSource(RuntimeEnvironment.getApplication())
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
    fun `test config gate disabled`() {
        val source = FakeDataSource(RuntimeEnvironment.getApplication())
        DataSourceState(
            factory = { source },
            configGate = { false },
        ).apply {
            currentSessionType = SessionType.FOREGROUND
        }

        // data capture is enabled by default.
        assertEquals(0, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)
    }

    @Test
    fun `test session type affects data capture`() {
        val source = FakeDataSource(RuntimeEnvironment.getApplication())
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
        assertEquals(0, source.resetCount)

        // new session should enable data capture
        state.currentSessionType = SessionType.FOREGROUND
        state.currentSessionType = SessionType.FOREGROUND
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)
        assertEquals(2, source.resetCount)

        // extra payload types should not re-register listeners
        state.currentSessionType = SessionType.BACKGROUND
        state.currentSessionType = SessionType.BACKGROUND
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(1, source.disableDataCaptureCount)
        assertEquals(4, source.resetCount)

        // functions can be called multiple times without issue
        state.currentSessionType = SessionType.FOREGROUND
        state.currentSessionType = SessionType.BACKGROUND
        assertEquals(2, source.enableDataCaptureCount)
        assertEquals(2, source.disableDataCaptureCount)
        assertEquals(6, source.resetCount)
    }
}
