package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
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

    @Test
    fun `capture data respects limits`() {
        val dst = FakeCurrentSessionSpan()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy({ 2 }))

        var count = 0
        repeat(4) {
            source.captureData {
                count++
            }
        }
        assertEquals(2, count)
    }

    private class FakeDataSourceImpl(
        dst: FakeCurrentSessionSpan,
        limitStrategy: LimitStrategy = NoopLimitStrategy
    ) :
        DataSourceImpl<FakeCurrentSessionSpan>(dst, limitStrategy) {

        override fun enableDataCapture() {
        }

        override fun disableDataCapture() {
        }
    }
}
