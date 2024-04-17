package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.opentelemetry.embExceptionHandling
import io.embrace.android.embracesdk.opentelemetry.exceptionMessage
import io.embrace.android.embracesdk.opentelemetry.exceptionStacktrace
import io.embrace.android.embracesdk.opentelemetry.exceptionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

internal fun assertOtelLogReceived(
    logReceived: Log?,
    message: String,
    severityNumber: Int,
    severityText: String,
    timeMs: Long = IntegrationTestRule.DEFAULT_SDK_START_TIME_MS,
    type: String? = null,
    exception: Throwable? = null,
    stack: List<StackTraceElement>? = null,
    properties: Map<String, Any>? = null
) {
    assertNotNull(logReceived)
    logReceived?.let { log ->
        assertEquals(message, log.body)
        assertEquals(severityNumber, log.severityNumber)
        assertEquals(severityText, log.severityText)
        assertEquals(timeMs * 1000000, log.timeUnixNano)
        type?.let { assertAttribute(log, embExceptionHandling.name, it) }
        exception?.let {
            assertAttribute(log, exceptionType.key, it.javaClass.simpleName)
            assertAttribute(log, exceptionMessage.key, it.message ?: "")
        }
        stack?.let {
            val stackString = it.map(StackTraceElement::toString).take(200).toList()
            val serializedStack = EmbraceSerializer().toJson(stackString, List::class.java)
            assertAttribute(log, exceptionStacktrace.key, serializedStack)
        }
        properties?.forEach { (key, value) ->
            assertAttribute(log, key, value.toString())
        }
    }
}

private fun assertAttribute(log: Log, name: String, expectedValue: String) {
    val attribute = log.attributes?.find { it.key == name }
    assertNotNull(attribute)
    assertEquals(expectedValue, attribute?.data)
}