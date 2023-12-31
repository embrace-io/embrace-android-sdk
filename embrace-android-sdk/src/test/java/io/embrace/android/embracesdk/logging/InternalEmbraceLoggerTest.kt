package io.embrace.android.embracesdk.logging

import io.embrace.android.embracesdk.fakes.FakeLoggerAction
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class InternalEmbraceLoggerTest {

    private val action = FakeLoggerAction()
    private var logger = InternalEmbraceLogger().apply {
        addLoggerAction(action)
    }

    @Before
    fun setUp() {
        ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED = false
    }

    @Test
    fun `a log with a severity that does not surpass the threshold does not trigger actions`() {
        // given the threshold is .INFO
        logger.setThreshold(Severity.INFO)

        // when log is called with a lower severity
        logger.log("test", Severity.DEBUG, Exception(), false)

        // then logger actions are not triggered
        assertTrue(action.msgQueue.isEmpty())
    }

    @Test
    fun `a log with the same severity as the threshold triggers logging actions`() {
        // given the threshold is .INFO
        logger.setThreshold(Severity.INFO)

        // when log is called with the same severity
        val throwable = Exception()
        logger.log("test", Severity.INFO, throwable, false)

        // then logger actions are triggered
        val msg = action.msgQueue.single()
        val expected = FakeLoggerAction.LogMessage("test", Severity.INFO, throwable, false)
        assertEquals(expected, msg)
    }

    @Test
    fun `a log with a higher severity than the threshold triggers logging actions`() {
        // given the threshold is .INFO
        logger.setThreshold(Severity.INFO)

        // when log is called with a higher severity
        val throwable = Exception()
        logger.log("test", Severity.WARNING, throwable, false)

        // then logger actions are triggered
        val msg = action.msgQueue.single()
        val expected = FakeLoggerAction.LogMessage("test", Severity.WARNING, throwable, false)
        assertEquals(expected, msg)
    }

    @Test
    fun `a log with lower severity than the threshold triggers actions when developer logging is enabled`() {
        // given the threshold is .INFO and developer logging is enabled
        logger.setThreshold(Severity.INFO)
        ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED = true

        // when log is called with a lower severity
        val throwable = Exception()
        logger.log("test", Severity.DEBUG, throwable, false)

        // then logger actions are triggered
        val msg = action.msgQueue.single()
        val expected = FakeLoggerAction.LogMessage("test", Severity.DEBUG, throwable, false)
        assertEquals(expected, msg)
    }
}
