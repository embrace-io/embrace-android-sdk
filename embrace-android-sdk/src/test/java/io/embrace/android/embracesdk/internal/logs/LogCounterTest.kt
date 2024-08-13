package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val TEST_LOG_LIMIT = 10

internal class LogCounterTest {

    private lateinit var logCounter: LogCounter

    private lateinit var fakeClock: FakeClock

    @Before
    fun setUp() {
        fakeClock = FakeClock()
        logCounter = LogCounter(
            "Test",
            fakeClock,
            { TEST_LOG_LIMIT },
            FakeEmbLogger()
        )
    }

    @Test
    fun `addIfAllowed increments count and adds log id if allowed`() {
        logCounter.addIfAllowed("1")
        assertTrue(logCounter.findLogIds().size == 1)
    }

    @Test
    fun `addIfAllowed returns false if log limit passed`() {
        // adding TEST_LOG_LIMIT logs works fine
        repeat(TEST_LOG_LIMIT) {
            fakeClock.tick(1000)
            assertTrue(logCounter.addIfAllowed(it.toString()))
        }
        assertTrue(logCounter.findLogIds().size == TEST_LOG_LIMIT)

        // adding TEST_LOG_LIMIT + 1 logs fails
        assertFalse(logCounter.addIfAllowed("11"))
    }

    @Test
    fun `clear resets count and log ids`() {
        logCounter.addIfAllowed("testLogId")
        assertTrue(logCounter.findLogIds().size == 1)
        logCounter.clear()
        assertTrue(logCounter.findLogIds().isEmpty())
    }
}