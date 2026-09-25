package io.embrace.android.embracesdk.internal.vitals

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.behavior.FakeVitalsBehavior
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.semconv.EmbFrameCountsAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `a foreground session part records its frame counts when it ends`() {
        val args = FakeInstrumentationArgs(ApplicationProvider.getApplicationContext())
        val dataSource = VitalsDataSource(args)
        dataSource.countFrame(dropped = true, expectedFrames = 7) // before the part started
        dataSource.onPostSessionChange()
        dataSource.countFrame(dropped = false, expectedFrames = 1)
        dataSource.countFrame(dropped = true, expectedFrames = 3)

        dataSource.onPreSessionEnd()

        assertEquals("2", args.destination.attributes[EmbFrameCountsAttributes.SMOOTHNESS_DROPPED_FRAMES])
        assertEquals("4", args.destination.attributes[EmbFrameCountsAttributes.SMOOTHNESS_EXPECTED_FRAMES])
    }

    @Test
    fun `a background session part records no frame counts, and does not carry them into the next part`() {
        val processStateTracker = FakeProcessStateTracker(ProcessState.BACKGROUND)
        val args =
            FakeInstrumentationArgs(ApplicationProvider.getApplicationContext(), processStateTracker = processStateTracker)
        val dataSource = VitalsDataSource(args)
        dataSource.onPostSessionChange()
        dataSource.countFrame(dropped = true, expectedFrames = 2)

        dataSource.onPreSessionEnd()
        assertTrue(args.destination.sessionPartAttributeWrites.isEmpty())

        // the app foregrounds: the next part counts only its own frames
        processStateTracker.state = ProcessState.FOREGROUND
        dataSource.onPostSessionChange()
        dataSource.countFrame(dropped = false, expectedFrames = 1)
        dataSource.onPreSessionEnd()

        assertEquals("0", args.destination.attributes[EmbFrameCountsAttributes.SMOOTHNESS_DROPPED_FRAMES])
        assertEquals("1", args.destination.attributes[EmbFrameCountsAttributes.SMOOTHNESS_EXPECTED_FRAMES])
    }
}
