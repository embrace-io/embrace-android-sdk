package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

internal class DataSourceImplTest {

    @Test
    fun `capture data successfully`() {
        val dst = FakeTelemetryDestination()
        val source = FakeDataSourceImpl(dst)
        var b = true
        source.captureTelemetry(inputValidation = { true }) {
            b = false
        }
        assertFalse(b)
    }

    @Test
    fun `capture data threw exception`() {
        val dst = FakeTelemetryDestination()
        val source = FakeDataSourceImpl(dst)

        // no exception thrown
        source.captureTelemetry(inputValidation = { true }) {
            error("Whoops!")
        }
    }

    @Test
    fun `capture data respects limits`() {
        val dst = FakeTelemetryDestination()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy { 2 })

        var count = 0
        repeat(4) {
            source.captureTelemetry(inputValidation = { true }) {
                count++
            }
        }
        assertEquals(2, count)
    }

    @Test
    fun `capture data respects validation`() {
        val dst = FakeTelemetryDestination()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy { 2 })

        var count = 0
        repeat(4) {
            source.captureTelemetry(inputValidation = { false }) {
                count++
            }
        }
        assertEquals(0, count)
    }

    private class FakeDataSourceImpl(
        dst: TelemetryDestination,
        limitStrategy: LimitStrategy = NoopLimitStrategy,
    ) : DataSourceImpl(
        dst,
        FakeEmbLogger(throwOnInternalError = false),
        limitStrategy
    )
}
