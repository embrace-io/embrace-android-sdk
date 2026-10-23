package io.embrace.android.embracesdk.internal.logs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val TEST_LOG_LIMIT = 10

internal class LogCounterTest {

    private lateinit var logCounter: LogCounter

    @Before
    fun setUp() {
        logCounter = LogCounter { TEST_LOG_LIMIT }
    }

    @Test
    fun `addIfAllowed increments count`() {
        logCounter.addIfAllowed()
        assertTrue(logCounter.getCount() == 1)
    }

    @Test
    fun `addIfAllowed returns false if log limit passed`() {
        // adding TEST_LOG_LIMIT logs works fine
        repeat(TEST_LOG_LIMIT) {
            assertTrue(logCounter.addIfAllowed())
        }
        assertTrue(logCounter.getCount() == TEST_LOG_LIMIT)

        // adding TEST_LOG_LIMIT + 1 logs fails
        assertFalse(logCounter.addIfAllowed())
        assertTrue(logCounter.getCount() == TEST_LOG_LIMIT)
    }

    @Test
    fun `a racing add cannot push the count over the limit`() {
        lateinit var counter: LogCounter
        var raced = false
        counter = LogCounter {
            if (!raced) {
                raced = true
                counter.addIfAllowed()
            }
            1
        }
        assertFalse(counter.addIfAllowed())
        assertTrue(counter.getCount() == 1)
    }

    @Test
    fun `clear resets count and log ids`() {
        logCounter.addIfAllowed()
        assertTrue(logCounter.getCount() == 1)
        logCounter.clear()
        assertTrue(logCounter.getCount() == 0)
    }
}
