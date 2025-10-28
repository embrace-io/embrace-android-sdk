package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeTraceWriter
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DataSourceImplTest {

    @Test
    fun `capture data successfully`() {
        val dst = FakeTraceWriter()
        val source = FakeDataSourceImpl(dst)
        val success = source.captureData(inputValidation = { true }) {
        }
        assertTrue(success)
    }

    @Test
    fun `capture data threw exception`() {
        val dst = FakeTraceWriter()
        val source = FakeDataSourceImpl(dst)
        val success = source.captureData(inputValidation = { true }) {
            error("Whoops!")
        }
        assertFalse(success)
    }

    @Test
    fun `capture data respects limits`() {
        val dst = FakeTraceWriter()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy { 2 })

        var count = 0
        repeat(4) {
            source.captureData(inputValidation = { true }) {
                count++
            }
        }
        assertEquals(2, count)
    }

    @Test
    fun `capture data respects validation`() {
        val dst = FakeTraceWriter()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy { 2 })

        var count = 0
        repeat(4) {
            source.captureData(inputValidation = { false }) {
                count++
            }
        }
        assertEquals(0, count)
    }

    private class FakeDataSourceImpl(
        dst: FakeTraceWriter,
        limitStrategy: LimitStrategy = NoopLimitStrategy,
    ) :
        DataSourceImpl<FakeTraceWriter>(
            dst,
            FakeEmbLogger(throwOnInternalError = false),
            limitStrategy
        ) {

        override fun enableDataCapture() {
        }

        override fun disableDataCapture() {
        }
    }
}
