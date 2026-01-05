@file:OptIn(ExperimentalApi::class)

package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.attrs.embExceptionHandling
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

@OptIn(ExperimentalApi::class, IncubatingApi::class)
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
            assertNull(log.attributes?.find { it.key == SessionAttributes.SESSION_ID })
        }
        expectedType?.let { assertAttribute(log, embExceptionHandling.name, it) }
        assertEquals(expectedState, log.attributes?.findAttributeValue(embState.name))
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

@OptIn(ExperimentalApi::class)
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
