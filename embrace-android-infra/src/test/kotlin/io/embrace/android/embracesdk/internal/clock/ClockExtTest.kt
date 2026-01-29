package io.embrace.android.embracesdk.internal.clock

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

internal class ClockExtTest {
    private lateinit var fakeClock: DriftClock
    private lateinit var fakeSystemClock: DriftClock

    @Before
    fun setup() {
        fakeClock = DriftClock({ 1692201601000L })
        fakeSystemClock = DriftClock(fakeClock)
    }

    @Test
    fun `check consistent offsets produce expected start and end times`() {
        val clockDrifts = listOf(-500L, -1L, 0L, 1L, 500L)
        clockDrifts.forEach { clockDrift ->
            validateOffset(
                clockDrift = clockDrift,
                driftDuringRequest = 0L,
                expectedOffset = clockDrift
            )
        }
    }

    @Test
    fun `check tick overs round to the lowest absolute value for the offset`() {
        val clockDrifts = listOf(-500L, -2L, -1L, 0L, 1L, 2L, 500L)
        val driftsDuringRequest = listOf(-1L, 1L)
        clockDrifts.forEach { clockDrift ->
            driftsDuringRequest.forEach { extraDrift ->
                validateOffset(
                    clockDrift = clockDrift,
                    driftDuringRequest = extraDrift,
                    expectedOffset = if (extraDrift * clockDrift >= 0) {
                        // clock drift shouldn't change if there's no existing drift or if the existing and extra drifts are the same sign
                        clockDrift
                    } else {
                        // clock drift should tick over if the extra drift increases the absolute magnitude of an existing drift
                        clockDrift + extraDrift
                    }
                )
            }
        }
    }

    @Test
    fun `check big differences in offset samples will result in no offset being used`() {
        val clockDrifts = listOf(-500L, -1L, 0L, 1L, 500L)
        val driftsDuringRequest = listOf(-200L, -2L, 2L, 200L)
        clockDrifts.forEach { clockDrift ->
            driftsDuringRequest.forEach { extraDrift ->
                validateOffset(
                    clockDrift = clockDrift,
                    driftDuringRequest = extraDrift,
                    expectedOffset = 0L
                )
            }
        }
    }

    private fun validateOffset(
        clockDrift: Long,
        driftDuringRequest: Long,
        expectedOffset: Long,
    ) {
        val realDrift = AtomicLong(clockDrift)
        fakeSystemClock.action = { realDrift.getAndAdd(driftDuringRequest) }
        val calculatedOffset = fakeClock.offset(fakeSystemClock)
        assertEquals(
            "For clockDrift $clockDrift and driftDuringRequest $driftDuringRequest, " +
                "expectedOffset $expectedOffset and calculatedOffset $calculatedOffset",
            0,
            expectedOffset + calculatedOffset
        )
    }

    private class DriftClock(
        private val baseClock: Clock,
        var action: () -> Long = { 0L }
    ) : Clock {
        override fun now(): Long = baseClock.now() + action()
    }
}
