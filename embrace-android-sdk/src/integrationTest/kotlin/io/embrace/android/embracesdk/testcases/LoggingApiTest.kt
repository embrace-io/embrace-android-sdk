package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.assertions.assertLogMessageReceived
import io.embrace.android.embracesdk.getLastSentLogMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.IllegalArgumentException

@RunWith(AndroidJUnit4::class)
internal class LoggingApiTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `log info message sent`() {
        with(testRule) {
            embrace.logInfo("test message")
            val eventMessage = harness.getLastSentLogMessage(expectedSize = 1)
            assertLogMessageReceived(
                eventMessage,
                message = "test message",
                eventType = EmbraceEvent.Type.INFO_LOG
            )
        }
    }

    @Test
    fun `log warning message sent`() {
        with(testRule) {
            embrace.logWarning("test message")
            val eventMessage = harness.getLastSentLogMessage(expectedSize = 1)
            assertLogMessageReceived(
                eventMessage,
                message = "test message",
                eventType = EmbraceEvent.Type.WARNING_LOG
            )
        }
    }

    @Test
    fun `log error message sent`() {
        with(testRule) {
            embrace.logError("test message")
            val eventMessage = harness.getLastSentLogMessage(expectedSize = 1)
            assertLogMessageReceived(
                eventMessage,
                message = "test message",
                eventType = EmbraceEvent.Type.ERROR_LOG
            )
        }
    }

    @Test
    fun `log messages with different severities sent`() {
        var logsSent = 0
        with(testRule) {
            Severity.values().forEach { severity ->
                val expectedMessage = "test message ${severity.name}"
                embrace.logMessage(expectedMessage, severity)
                logsSent++
                val eventMessage = harness.getLastSentLogMessage(logsSent)
                assertLogMessageReceived(
                    eventMessage,
                    message = expectedMessage,
                    eventType = EmbraceEvent.Type.fromSeverity(severity)
                )
            }
        }
    }

    @Test
    fun `log messages with different severities and properties sent`() {
        var logsSent = 0
        with(testRule) {
            Severity.values().forEach { severity ->
                val expectedMessage = "test message ${severity.name}"
                embrace.logMessage(expectedMessage, severity, customProperties)
                logsSent++
                val eventMessage = harness.getLastSentLogMessage(logsSent)
                assertLogMessageReceived(
                    eventMessage,
                    message = expectedMessage,
                    eventType = EmbraceEvent.Type.fromSeverity(severity),
                    properties = customProperties
                )
            }
        }
    }

    @Test
    fun `log exception message sent`() {
        with(testRule) {
            embrace.logException(testException)
            val eventMessage = harness.getLastSentLogMessage(expectedSize = 1)
            assertLogMessageReceived(
                eventMessage,
                message = checkNotNull(testException.message),
                eventType = EmbraceEvent.Type.ERROR_LOG,
                logType = LogExceptionType.HANDLED,
                exception = testException
            )
        }
    }

    @Test
    fun `log exception with different severities sent`() {
        var logsSent = 0
        with(testRule) {
            Severity.values().forEach { severity ->
                embrace.logException(testException, severity)
                logsSent++
                val eventMessage = harness.getLastSentLogMessage(logsSent)
                assertLogMessageReceived(
                    eventMessage,
                    message = checkNotNull(testException.message),
                    eventType = EmbraceEvent.Type.fromSeverity(severity),
                    logType = LogExceptionType.HANDLED,
                    exception = testException
                )
            }
        }
    }

    @Test
    fun `log exception with different severities and properties sent`() {
        var logsSent = 0
        with(testRule) {
            Severity.values().forEach { severity ->
                embrace.logException(testException, severity, customProperties)
                logsSent++
                val eventMessage = harness.getLastSentLogMessage(logsSent)
                assertLogMessageReceived(
                    eventMessage,
                    message = checkNotNull(testException.message),
                    eventType = EmbraceEvent.Type.fromSeverity(severity),
                    properties = customProperties,
                    logType = LogExceptionType.HANDLED,
                    exception = testException
                )
            }
        }
    }

    @Test
    fun `log exception with different severities, properties, and custom message sent`() {
        var logsSent = 0
        with(testRule) {
            Severity.values().forEach { severity ->
                val expectedMessage = "test message ${severity.name}"
                embrace.logException(testException, severity, customProperties, expectedMessage)
                logsSent++
                val eventMessage = harness.getLastSentLogMessage(logsSent)
                assertLogMessageReceived(
                    eventMessage,
                    message = expectedMessage,
                    eventType = EmbraceEvent.Type.fromSeverity(severity),
                    properties = customProperties,
                    logType = LogExceptionType.HANDLED,
                    exception = testException
                )
            }
        }
    }

    @Test
    fun `log custom stacktrace message sent`() {
        with(testRule) {
            embrace.logCustomStacktrace(stacktrace)
            val eventMessage = harness.getLastSentLogMessage(expectedSize = 1)
            assertLogMessageReceived(
                eventMessage,
                message = "",
                eventType = EmbraceEvent.Type.ERROR_LOG,
                logType = LogExceptionType.HANDLED,
                stack = stacktrace
            )
        }
    }

    @Test
    fun `log custom stacktrace with different severities sent`() {
        var logsSent = 0
        with(testRule) {
            Severity.values().forEach { severity ->
                embrace.logCustomStacktrace(stacktrace, severity)
                logsSent++
                val eventMessage = harness.getLastSentLogMessage(logsSent)
                assertLogMessageReceived(
                    eventMessage,
                    message = "",
                    eventType = EmbraceEvent.Type.fromSeverity(severity),
                    logType = LogExceptionType.HANDLED,
                    stack = stacktrace
                )
            }
        }
    }

    @Test
    fun `log custom stacktrace with different severities and properties sent`() {
        var logsSent = 0
        with(testRule) {
            Severity.values().forEach { severity ->
                embrace.logCustomStacktrace(stacktrace, severity, customProperties)
                logsSent++
                val eventMessage = harness.getLastSentLogMessage(logsSent)
                assertLogMessageReceived(
                    eventMessage,
                    message = "",
                    eventType = EmbraceEvent.Type.fromSeverity(severity),
                    properties = customProperties,
                    logType = LogExceptionType.HANDLED,
                    stack = stacktrace
                )
            }
        }
    }

    @Test
    fun `log custom stacktrace with different severities, properties, and custom message sent`() {
        var logsSent = 0
        with(testRule) {
            Severity.values().forEach { severity ->
                val expectedMessage = "test message ${severity.name}"
                embrace.logCustomStacktrace(stacktrace, severity, customProperties, expectedMessage)
                logsSent++
                val eventMessage = harness.getLastSentLogMessage(logsSent)
                assertLogMessageReceived(
                    eventMessage,
                    message = expectedMessage,
                    eventType = EmbraceEvent.Type.fromSeverity(severity),
                    properties = customProperties,
                    logType = LogExceptionType.HANDLED,
                    stack = stacktrace
                )
            }
        }
    }

    companion object {
        private val testException = IllegalArgumentException("nooooooo")
        private val customProperties: Map<String, Any> = linkedMapOf(Pair("first", 1), Pair("second", "two"), Pair("third", true))
        private val stacktrace = Thread.currentThread().stackTrace
    }
}
