package io.embrace.android.embracesdk.anr.detection

import io.embrace.android.embracesdk.fakes.FakeLogAction
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class UnbalancedCallDetectorTest {

    private lateinit var logger: EmbLoggerImpl
    private lateinit var detector: UnbalancedCallDetector
    private lateinit var action: FakeLogAction
    private val thread = Thread.currentThread()

    @Before
    fun setUp() {
        action = FakeLogAction()
        logger = EmbLoggerImpl().apply { addLoggerAction(action) }
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
        verifyInternalErrorLogs(0)
    }

    @Test
    fun testUnbalancedOnThreadBlocked() {
        detector.onThreadBlocked(thread, 1)
        detector.onThreadBlocked(thread, 2)
        verifyInternalErrorLogs(1)
    }

    @Test
    fun testUnbalancedOnThreadBlockedInterval() {
        detector.onThreadBlockedInterval(thread, 1)
        verifyInternalErrorLogs(1)
    }

    @Test
    fun testUnbalancedOnThreadUnblocked() {
        detector.onThreadUnblocked(thread, 1)
        verifyInternalErrorLogs(1)
    }

    @Test
    fun testOnThreadBlockedWrongTimestamp() {
        detector.onThreadBlocked(thread, 150000000)
        detector.onThreadBlockedInterval(thread, 150000001)
        detector.onThreadUnblocked(thread, 150000002)
        detector.onThreadBlocked(thread, 140000000)
        verifyInternalErrorLogs(1)
    }

    @Test
    fun testOnThreadBlockedIntervalWrongTimestamp() {
        detector.onThreadBlocked(thread, 150000000)
        detector.onThreadBlockedInterval(thread, 140000000)
        verifyInternalErrorLogs(1)
    }

    @Test
    fun testOnThreadUnblockedWrongTimestamp() {
        detector.onThreadBlocked(thread, 150000000)
        detector.onThreadBlockedInterval(thread, 150000001)
        detector.onThreadUnblocked(thread, 140000000)
        verifyInternalErrorLogs(1)
    }

    private fun verifyInternalErrorLogs(expectedCount: Int) {
        val messages = action.msgQueue.filter { msg ->
            msg.severity == EmbLoggerImpl.Severity.ERROR && msg.logStacktrace
        }
        assertEquals(expectedCount, messages.size)
    }
}
