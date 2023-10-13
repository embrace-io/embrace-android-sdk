package io.embrace.android.embracesdk.anr.sigquit

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class GoogleAnrTimestampRepositoryTest {
    private val testTimestamp = 1234L
    private val differentTimestamp = 1000L

    private val mockLogger = mockk<InternalEmbraceLogger>(relaxed = true)

    private val googleAnrTimestampRepository = GoogleAnrTimestampRepository(mockLogger)

    @Test
    fun `disregard new timestamps after limit has been reached`() {
        repeat(50) {
            googleAnrTimestampRepository.add(testTimestamp)
        }
        googleAnrTimestampRepository.add(differentTimestamp)

        val timestamps = googleAnrTimestampRepository.getGoogleAnrTimestamps(0L, 10000L)
        assertEquals(50, timestamps.size)
        assertFalse(timestamps.contains(differentTimestamp))
        verify { mockLogger.logWarning(any()) }
    }

    @Test
    fun `clear removes saved ANRs`() {
        googleAnrTimestampRepository.add(testTimestamp)

        googleAnrTimestampRepository.clear()

        val timestamps = googleAnrTimestampRepository.getGoogleAnrTimestamps(0L, 10000L)
        assertTrue(timestamps.isEmpty())
    }

    @Test
    fun `return empty list if startTime is bigger than endTime`() {
        val startTime = 2L
        val endTime = 1L
        googleAnrTimestampRepository.add(testTimestamp)

        val timestamps = googleAnrTimestampRepository.getGoogleAnrTimestamps(startTime, endTime)

        assertEquals(emptyList<Long>(), timestamps)
    }

    @Test
    fun `return only timestamps within the extended start and end range`() {
        val startTime = 10L
        val endTime = 20L
        val timestampWithin = 15L
        val timestampWithinExtendedStart = 5L
        val timestampWithinExtendedEnd = 25L
        val timestampHigherThanExtendedEndTime = 26L
        val timestampLowerThanExtendedStartTime = 4L

        googleAnrTimestampRepository.add(timestampWithin)
        googleAnrTimestampRepository.add(timestampWithinExtendedStart)
        googleAnrTimestampRepository.add(timestampWithinExtendedEnd)
        googleAnrTimestampRepository.add(timestampHigherThanExtendedEndTime)
        googleAnrTimestampRepository.add(timestampLowerThanExtendedStartTime)

        val timestamps = googleAnrTimestampRepository.getGoogleAnrTimestamps(startTime, endTime)

        assertTrue(timestamps.contains(timestampWithin))
        assertTrue(timestamps.contains(timestampWithinExtendedStart))
        assertTrue(timestamps.contains(timestampWithinExtendedEnd))
        assertFalse(timestamps.contains(timestampLowerThanExtendedStartTime))
        assertFalse(timestamps.contains(timestampHigherThanExtendedEndTime))
    }

    @Test
    fun `stop adding timestamps after getting one that exceeds endTime`() {
        val startTime = 1L
        val endTime = 4L
        val timestampWithin1 = 2L
        val timestampExceedingExtendedEndTime = 11L
        val timestampWithin2 = 3L
        googleAnrTimestampRepository.add(timestampWithin1)
        googleAnrTimestampRepository.add(timestampExceedingExtendedEndTime)
        googleAnrTimestampRepository.add(timestampWithin2)

        val timestamps = googleAnrTimestampRepository.getGoogleAnrTimestamps(startTime, endTime)
        assertTrue(timestamps.contains(timestampWithin1))
        assertFalse(timestamps.contains(timestampExceedingExtendedEndTime))
        assertFalse(timestamps.contains(timestampWithin2))
    }
}
