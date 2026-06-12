
package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.opentelemetry.kotlin.semconv.LogAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

fun assertOtelLogReceived(
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
    hasSession: Boolean = true,
) {
    assertNotNull(logReceived)
    logReceived?.let { log ->
        assertEquals(expectedEmbType, log.attributes?.find { it.key == "emb.type" }?.data)
        assertEquals(expectedMessage, log.body)
        assertEquals(expectedSeverityNumber.severityNumber, log.severityNumber)
        assertEquals(expectedSeverityText ?: expectedSeverityNumber.name, log.severityText)
        assertEquals(expectedTimeMs.millisToNanos(), log.timeUnixNano)
        if (hasSession) {
            assertFalse(log.attributes?.findAttributeValue(SessionAttributes.SESSION_ID).isNullOrBlank())
        } else {
            val sessionId = log.attributes?.findAttributeValue(SessionAttributes.SESSION_ID)
            assertEquals("", sessionId ?: "")
        }
        expectedType?.let { assertAttribute(log, EmbAndroidAttributes.EMB_EXCEPTION_HANDLING, it) }
        assertEquals(expectedState, log.attributes?.findAttributeValue(EmbSessionAttributes.EMB_STATE))
        expectedExceptionName?.let {
            assertAttribute(log, ExceptionAttributes.EXCEPTION_TYPE, expectedExceptionName)
        }
        expectedExceptionMessage?.let {
            assertAttribute(log, ExceptionAttributes.EXCEPTION_MESSAGE, expectedExceptionMessage)
        }
        expectedStacktrace?.let {
            val serializedStack = EmbraceSerializer().truncatedStacktrace(it.toTypedArray())
            assertAttribute(log, ExceptionAttributes.EXCEPTION_STACKTRACE, serializedStack)
        }
        assertNotNull(expectedEmbType, log.attributes?.single { it.key == LogAttributes.LOG_RECORD_UID }?.data)
        expectedProperties?.forEach { (key, value) ->
            assertAttribute(log, key, value.toString())
        }
    }
}

fun getOtelSeverity(severity: Severity): SeverityNumber {
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
