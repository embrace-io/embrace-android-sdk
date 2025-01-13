package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.opentelemetry.embExceptionHandling
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull

internal fun assertOtelLogReceived(
    logReceived: Log?,
    expectedMessage: String,
    expectedSeverityNumber: Int,
    expectedSeverityText: String,
    expectedTimeMs: Long = IntegrationTestRule.DEFAULT_SDK_START_TIME_MS,
    expectedType: String? = null,
    expectedExceptionName: String? = null,
    expectedExceptionMessage: String? = null,
    expectedStacktrace: List<StackTraceElement>? = null,
    expectedProperties: Map<String, Any>? = null,
    expectedEmbType: String = "sys.log",
    expectedState: String = "background",
) {
    assertNotNull(logReceived)
    logReceived?.let { log ->
        assertEquals(expectedEmbType, log.attributes?.find { it.key == "emb.type" }?.data)
        assertEquals(expectedMessage, log.body)
        assertEquals(expectedSeverityNumber, log.severityNumber)
        assertEquals(expectedSeverityText, log.severityText)
        assertEquals(expectedTimeMs * 1000000, log.timeUnixNano)
        assertFalse(log.attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key).isNullOrBlank())
        expectedType?.let { assertAttribute(log, embExceptionHandling.name, it) }
        assertEquals(expectedState, log.attributes?.findAttributeValue(embState.attributeKey.key))
        expectedExceptionName?.let {
            assertAttribute(log, ExceptionAttributes.EXCEPTION_TYPE.key, expectedExceptionName)
        }
        expectedExceptionMessage?.let {
            assertAttribute(log, ExceptionAttributes.EXCEPTION_MESSAGE.key, expectedExceptionMessage)
        }
        expectedStacktrace?.let {
            val serializedStack = EmbraceSerializer().truncatedStacktrace(it.toTypedArray())
            assertAttribute(log, ExceptionAttributes.EXCEPTION_STACKTRACE.key, serializedStack)
        }
        expectedProperties?.forEach { (key, value) ->
            assertAttribute(log, key, value.toString())
        }
    }
}

internal fun getOtelSeverity(severity: Severity): io.opentelemetry.api.logs.Severity {
    return when (severity) {
        Severity.INFO -> io.opentelemetry.api.logs.Severity.INFO
        Severity.WARNING -> io.opentelemetry.api.logs.Severity.WARN
        Severity.ERROR -> io.opentelemetry.api.logs.Severity.ERROR
    }
}

private fun assertAttribute(log: Log, name: String, expectedValue: String) {
    val attribute = log.attributes?.find { it.key == name }
    assertNotNull("Attribute not found: $name", attribute)
    assertEquals(expectedValue, attribute?.data)
}
