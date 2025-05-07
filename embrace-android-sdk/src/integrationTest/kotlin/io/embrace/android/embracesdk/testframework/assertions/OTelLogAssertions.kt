package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.attrs.embExceptionHandling
import io.embrace.android.embracesdk.internal.otel.attrs.embState
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull

internal fun assertOtelLogReceived(
    logReceived: Log?,
    expectedMessage: String,
    expectedSeverityNumber: SeverityNumber,
    expectedTimeMs: Long,
    expectedSeverityText: String? = null,
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
        assertEquals(expectedSeverityNumber.severityNumber, log.severityNumber)
        assertEquals(expectedSeverityText ?: expectedSeverityNumber.name, log.severityText)
        assertEquals(expectedTimeMs.millisToNanos(), log.timeUnixNano)
        assertFalse(log.attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key).isNullOrBlank())
        expectedType?.let { assertAttribute(log, embExceptionHandling.name, it) }
        assertEquals(expectedState, log.attributes?.findAttributeValue(embState.name))
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

internal fun getOtelSeverity(severity: Severity): SeverityNumber {
    return when (severity) {
        Severity.INFO -> SeverityNumber.INFO
        Severity.WARNING -> SeverityNumber.WARN
        Severity.ERROR -> SeverityNumber.ERROR
    }
}

private fun assertAttribute(log: Log, name: String, expectedValue: String) {
    val attribute = log.attributes?.find { it.key == name }
    assertNotNull("Attribute not found: $name", attribute)
    assertEquals(expectedValue, attribute?.data)
}
