package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import org.junit.Assert.assertEquals
import org.junit.Test

internal class DataSourceImplTest {

    @Test
    fun `capture data successfully`() {
        val dst = FakeCurrentSessionSpan()
        val source = FakeDataSourceImpl(dst)
        source.captureData {
            initialized()
        }
        assertEquals(1, dst.initializedCallCount)
    }

    @Test
    fun `capture data threw exception`() {
        val dst = FakeCurrentSessionSpan()
        val source = FakeDataSourceImpl(dst)
        source.captureData {
            error("Whoops!")
        }
        assertEquals(0, dst.initializedCallCount)
    }

    private class FakeDataSourceImpl(dst: FakeCurrentSessionSpan) :
        DataSourceImpl<FakeCurrentSessionSpan>(dst) {

        override fun enableDataCapture() {
        }

        override fun disableDataCapture() {
        }
    }
}
