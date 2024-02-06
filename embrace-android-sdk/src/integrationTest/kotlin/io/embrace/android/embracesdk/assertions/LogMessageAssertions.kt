package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.LogExceptionType
import org.junit.Assert.assertEquals

/**
 * Asserts that a log message was sent with the given parameters.
 */
internal fun assertLogMessageReceived(
    eventMessage: EventMessage,
    message: String,
    eventType: EventType,
    logType: LogExceptionType = LogExceptionType.NONE,
    timeMs: Long = IntegrationTestRule.DEFAULT_SDK_START_TIME_MS,
    properties: Map<String, Any>? = null,
    exception: Exception? = null,
    stack: Array<StackTraceElement>? = null
) {
    with(eventMessage.event) {
        assertEquals(message, name)
        assertEquals(timeMs, timestamp)
        assertEquals(false, screenshotTaken)
        assertEquals(logType.value, logExceptionType)
        assertEquals(eventType, type)
        assertEquals(Embrace.AppFramework.NATIVE.value, framework)
        assertEquals(properties, customProperties)
        exception?.let {
            assertEquals(it.message, exceptionMessage)
            assertEquals(it.javaClass.simpleName, exceptionName)
        }
    }

    if (stack != null) {
        assertEquals(stack.map { it.toString() }, eventMessage.stacktraces?.jvmStacktrace)
    }
}