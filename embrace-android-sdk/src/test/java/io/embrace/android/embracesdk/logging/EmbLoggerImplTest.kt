package io.embrace.android.embracesdk.logging

import io.embrace.android.embracesdk.fakes.FakeLogAction
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.logging.EmbLoggerImpl.Severity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbLoggerImplTest {

    private val action = FakeLogAction()
    private var logger = EmbLoggerImpl().apply {
        addLoggerAction(action)
    }

    @Before
    fun setUp() {
        ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED = false
    }

    @Test
    fun `a log with the same severity as the threshold triggers logging actions`() {
        // when log is called with the same severity
        logger.logInfo("test")

        // then logger actions are triggered
        val msg = action.msgQueue.single()
        val expected = FakeLogAction.LogMessage("test", Severity.INFO, null, false)
        assertEquals(expected, msg)
    }

    @Test
    fun `a log with a higher severity than the threshold triggers logging actions`() {
        // when log is called with a higher severity
        val throwable = Exception()
        logger.logWarning("test", throwable, false)

        // then logger actions are triggered
        val msg = action.msgQueue.single()
        val expected = FakeLogAction.LogMessage("test", Severity.WARNING, throwable, false)
        assertEquals(expected, msg)
    }

    @Test
    fun `a log with lower severity than the threshold triggers actions when developer logging is enabled`() {
        // given the threshold is .INFO and developer logging is enabled
        ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED = true

        // when log is called with a lower severity
        val throwable = Exception()
        logger.logDebug("test", throwable)

        // then logger actions are triggered
        val msg = action.msgQueue.single()
        val expected = FakeLogAction.LogMessage("test", Severity.DEBUG, throwable, true)
        assertEquals(expected, msg)
    }
}
