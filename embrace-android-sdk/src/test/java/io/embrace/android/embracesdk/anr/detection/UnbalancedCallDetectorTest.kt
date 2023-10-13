package io.embrace.android.embracesdk.anr.detection

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.mockk.MockKVerificationScope
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

internal class UnbalancedCallDetectorTest {

    private lateinit var logger: InternalEmbraceLogger
    private lateinit var detector: UnbalancedCallDetector
    private val thread = Thread.currentThread()

    @Before
    fun setUp() {
        logger = mockk(relaxUnitFun = true)
        detector = UnbalancedCallDetector(logger)
    }

    @Test
    fun testBalancedCalls() {
        detector.onThreadBlocked(thread, 1)
        detector.onThreadBlockedInterval(thread, 2)
        detector.onThreadBlockedInterval(thread, 3)
        detector.onThreadBlockedInterval(thread, 4)
        detector.onThreadUnblocked(thread, 5)
        detector.onThreadBlocked(thread, 6)
        verify(exactly = 0) { internalErrorLog() }
    }

    @Test
    fun testUnbalancedOnThreadBlocked() {
        detector.onThreadBlocked(thread, 1)
        detector.onThreadBlocked(thread, 2)
        verify(exactly = 1) { internalErrorLog() }
    }

    @Test
    fun testUnbalancedOnThreadBlockedInterval() {
        detector.onThreadBlockedInterval(thread, 1)
        verify(exactly = 1) { internalErrorLog() }
    }

    @Test
    fun testUnbalancedOnThreadUnblocked() {
        detector.onThreadUnblocked(thread, 1)
        verify(exactly = 1) { internalErrorLog() }
    }

    @Test
    fun testOnThreadBlockedWrongTimestamp() {
        detector.onThreadBlocked(thread, 150000000)
        detector.onThreadBlockedInterval(thread, 150000001)
        detector.onThreadUnblocked(thread, 150000002)
        detector.onThreadBlocked(thread, 140000000)
        verify(exactly = 1) { internalErrorLog() }
    }

    @Test
    fun testOnThreadBlockedIntervalWrongTimestamp() {
        detector.onThreadBlocked(thread, 150000000)
        detector.onThreadBlockedInterval(thread, 140000000)
        verify(exactly = 1) { internalErrorLog() }
    }

    @Test
    fun testOnThreadUnblockedWrongTimestamp() {
        detector.onThreadBlocked(thread, 150000000)
        detector.onThreadBlockedInterval(thread, 150000001)
        detector.onThreadUnblocked(thread, 140000000)
        verify(exactly = 1) { internalErrorLog() }
    }

    private fun MockKVerificationScope.internalErrorLog() =
        logger.log(any(), InternalStaticEmbraceLogger.Severity.ERROR, any(), true)
}
