package io.embrace.android.embracesdk.internal.clock

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class NormalizedIntervalClockTest {

    private var elapsedMs = 10_000L
    private val elapsedProvider = { elapsedMs }
    private lateinit var errorHandler: FakeErrorHandler

    @Before
    fun setUp() {
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
    }

    @Test
    fun `no drift - no error logged`() {
        val clock = buildClock()
        clock.now()
        elapsedMs += 1_000L
        clock.now()
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `drift below threshold - no error logged`() {
        val clock = buildClock(driftThresholdMs = 60_000L)
        elapsedMs += 70_000L
        clock.now() // record high-water mark
        elapsedMs -= 59_999L // drift back by just under 60s
        clock.now()
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `drift above threshold - error logged`() {
        val clock = buildClock(driftThresholdMs = 60_000L)
        elapsedMs += 70_000L
        clock.now() // record high-water mark
        elapsedMs -= 60_001L // drift back by just over 60s
        clock.now()
        assertEquals(1, errorHandler.sdkErrorMessages.size)
    }

    @Test
    fun `no logger - no crash on drift`() {
        val clock = NormalizedIntervalClock(
            errorHandler = null,
            monotonicClock = elapsedProvider,
        )
        elapsedMs += 70_000L
        clock.now()
        elapsedMs -= 61_000L
        clock.now() // must not throw
    }

    @Test
    fun `custom threshold - below does not log`() {
        val clock = buildClock(driftThresholdMs = 1_000L)
        elapsedMs += 5_000L
        clock.now()
        elapsedMs -= 500L // 500ms back, under 1s threshold
        clock.now()
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `custom threshold - above does log`() {
        val clock = buildClock(driftThresholdMs = 1_000L)
        elapsedMs += 5_000L
        clock.now()
        elapsedMs -= 1_001L // 1001ms back, over 1s threshold
        clock.now()
        assertEquals(1, errorHandler.sdkErrorMessages.size)
    }

    @Test
    fun `drift logged only once across multiple drifts`() {
        val clock = buildClock(driftThresholdMs = 60_000L)
        elapsedMs += 70_000L
        clock.now() // record high-water mark
        elapsedMs -= 60_001L // first drift above threshold
        clock.now()
        elapsedMs += 70_000L
        clock.now() // new high-water mark
        elapsedMs -= 60_001L // second drift above threshold
        clock.now()
        assertEquals(1, errorHandler.sdkErrorMessages.size)
    }

    @Test
    fun `first call never triggers error`() {
        val clock = buildClock()
        elapsedMs = 0L // very low elapsed time
        clock.now() // no previous value, must not log
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    private fun buildClock(driftThresholdMs: Long = NormalizedIntervalClock.DEFAULT_DRIFT_THRESHOLD_MS) =
        NormalizedIntervalClock(
            errorHandler = errorHandler,
            driftThresholdMs = driftThresholdMs,
            monotonicClock = elapsedProvider,
        )
}
