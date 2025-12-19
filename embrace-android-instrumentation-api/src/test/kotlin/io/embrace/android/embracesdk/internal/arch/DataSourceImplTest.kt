package io.embrace.android.embracesdk.internal.arch

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class DataSourceImplTest {

    @Test
    fun `capture data successfully`() {
        val source = FakeDataSourceImpl()
        var b = true
        var validationFailed = 0
        source.captureTelemetry(
            inputValidation = { true },
            invalidInputCallback = { validationFailed++ }
        ) {
            b = false
        }
        assertFalse(b)
        assertEquals(0, validationFailed)
    }

    @Test
    fun `capture data threw exception`() {
        val source = FakeDataSourceImpl()

        // no exception thrown
        source.captureTelemetry {
            error("Whoops!")
        }
    }

    @Test
    fun `capture data respects limits`() {
        val source = FakeDataSourceImpl(UpToLimitStrategy { 2 })

        var count = 0
        repeat(4) {
            source.captureTelemetry {
                count++
            }
        }
        assertEquals(2, count)
    }

    @Test
    fun `capture data respects validation`() {
        val source = FakeDataSourceImpl(UpToLimitStrategy { 2 })
        var validationFailed = 0

        var count = 0
        repeat(4) {
            source.captureTelemetry(
                inputValidation = { false },
                invalidInputCallback = { validationFailed++ }
            ) {
                count++
            }
        }

        assertEquals(0, count)
        assertEquals(4, validationFailed)
    }

    private class FakeDataSourceImpl(
        limitStrategy: LimitStrategy = NoopLimitStrategy,
        args: FakeInstrumentationArgs = FakeInstrumentationArgs(
            ApplicationProvider.getApplicationContext(),
            logger = FakeEmbLogger(throwOnInternalError = false)
        ),
    ) : DataSourceImpl(
        args,
        limitStrategy,
        "test_data_source"
    )
}
