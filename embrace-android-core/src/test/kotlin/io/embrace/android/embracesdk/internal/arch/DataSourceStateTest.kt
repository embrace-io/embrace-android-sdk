package io.embrace.android.embracesdk.internal.arch

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class DataSourceStateTest {

    @Test
    fun `test config gate defaults to enabled`() {
        val source = FakeDataSource(RuntimeEnvironment.getApplication())
        DataSourceState(
            factory = { source },
        )

        // data capture is enabled by default.
        assertEquals(1, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)
    }

    @Test
    fun `test config gate enabled`() {
        val source = FakeDataSource(RuntimeEnvironment.getApplication())
        DataSourceState(
            factory = { source },
            configGate = { true },
        )

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
        )

        // data capture is enabled by default.
        assertEquals(0, source.enableDataCaptureCount)
        assertEquals(0, source.disableDataCaptureCount)
    }
}
