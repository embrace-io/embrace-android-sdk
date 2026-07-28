package io.embrace.android.embracesdk.internal.vitals

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.behavior.FakeVitalsBehavior
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class VitalsDataSourceTest {

    private val telemetryService = FakeTelemetryService()

    private fun createDataSource(spanLimit: Int): Pair<VitalsDataSource, FakeInstrumentationArgs> {
        val application: Application = ApplicationProvider.getApplicationContext()
        val args = FakeInstrumentationArgs(application, telemetryService = telemetryService)
        args.configService.vitalsBehavior = FakeVitalsBehavior(spanLimitImpl = spanLimit)
        return VitalsDataSource(args) to args
    }

    /**
     * Emits [count] spans through the data source's limit strategy — the same path the smoothness and
     * screen-load results take.
     */
    private fun VitalsDataSource.emit(count: Int) {
        repeat(count) {
            captureTelemetry {
                recordCompletedSpan(name = "smoothness", startTimeMs = 0L, endTimeMs = 1L)
            }
        }
    }

    @Test
    fun `spans are emitted up to the configured limit and then dropped`() {
        val (dataSource, args) = createDataSource(spanLimit = 3)

        dataSource.emit(5)

        assertEquals(3, args.destination.completedSpans().size)
    }

    @Test
    fun `the limit is shared across both vitals span types`() {
        val (dataSource, args) = createDataSource(spanLimit = 250)

        dataSource.emit(400)

        assertEquals(250, args.destination.completedSpans().size)
    }

    @Test
    fun `the limit resets on a session part change`() {
        val (dataSource, args) = createDataSource(spanLimit = 3)
        dataSource.emit(5)

        dataSource.resetDataCaptureLimits()
        dataSource.emit(5)

        assertEquals(6, args.destination.completedSpans().size)
    }

    @Test
    fun `a dropped span is tracked as an applied limit`() {
        val (dataSource, args) = createDataSource(spanLimit = 1)

        dataSource.emit(3)

        assertEquals(1, args.destination.completedSpans().size)
        assertEquals(
            List(2) { "vitals_data_source" to AppliedLimitType.DROP },
            telemetryService.appliedLimits,
        )
    }
}
