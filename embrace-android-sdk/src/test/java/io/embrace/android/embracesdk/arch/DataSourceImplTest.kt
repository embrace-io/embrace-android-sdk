package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DataSourceImplTest {

    @Test
    fun `capture data successfully`() {
        val dst = FakeCurrentSessionSpan()
        val source = FakeDataSourceImpl(dst)
        val success = source.alterSessionSpan(inputValidation = { true }) {
            initialized()
        }
        assertTrue(success)
        assertEquals(1, dst.initializedCallCount)
    }

    @Test
    fun `capture data threw exception`() {
        val dst = FakeCurrentSessionSpan()
        val source = FakeDataSourceImpl(dst)
        val success = source.alterSessionSpan(inputValidation = { true }) {
            error("Whoops!")
        }
        assertFalse(success)
        assertEquals(0, dst.initializedCallCount)
    }

    @Test
    fun `capture data respects limits`() {
        val dst = FakeCurrentSessionSpan()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy(InternalEmbraceLogger()) { 2 })

        var count = 0
        repeat(4) {
            source.alterSessionSpan(inputValidation = { true }) {
                count++
            }
        }
        assertEquals(2, count)
    }

    @Test
    fun `capture data respects validation`() {
        val dst = FakeCurrentSessionSpan()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy(InternalEmbraceLogger()) { 2 })

        var count = 0
        repeat(4) {
            source.alterSessionSpan(inputValidation = { false }) {
                count++
            }
        }
        assertEquals(0, count)
    }

    private class FakeDataSourceImpl(
        dst: FakeCurrentSessionSpan,
        limitStrategy: LimitStrategy = NoopLimitStrategy
    ) :
        DataSourceImpl<FakeCurrentSessionSpan>(dst, InternalEmbraceLogger(), limitStrategy) {

        override fun enableDataCapture() {
        }

        override fun disableDataCapture() {
        }
    }
}
