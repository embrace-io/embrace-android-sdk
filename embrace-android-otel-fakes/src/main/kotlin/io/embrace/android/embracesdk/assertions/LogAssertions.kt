
package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.opentelemetry.kotlin.semconv.LogAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull

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
            assertFalse(log.attributes?.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_ID).isNullOrBlank())
        } else {
            val userSessionId = log.attributes?.findAttributeValue(EmbSessionAttributes.EMB_USER_SESSION_ID)
            assertEquals("", userSessionId ?: "")
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

/**
 * Validate that a log is recorded under [stateKey] with the system value [value] as the state value, along with its value type.
 */
fun Log.assertSystemStateValue(stateKey: String, value: Any): Unit =
    stateAttributes().assertSystemStateValue(value, stateKey, stateValueTypeKey(stateKey))

/**
 * Validate that a log is recorded under [stateKey] with the non-system value [value] as the state value, with no value type.
 */
fun Log.assertNonSystemStateValue(stateKey: String, value: Any): Unit =
    stateAttributes().assertNonSystemStateValue(value, stateKey, stateValueTypeKey(stateKey))

/**
 * Validate that a log is not recorded with a state value under [stateKey], nor its value type.
 */
fun Log.assertNoStateValue(stateKey: String) {
    with(stateAttributes()) {
        assertFalse(containsKey(stateKey))
        assertFalse(containsKey(stateValueTypeKey(stateKey)))
    }
}

private fun Log.stateAttributes(): Map<String, String> = checkNotNull(attributes).toMap()

private fun stateValueTypeKey(stateKey: String): String = "$stateKey.value_type"

private fun assertAttribute(log: Log, name: String, expectedValue: String) {
    val attribute = log.attributes?.find { it.key == name }
    assertNotNull("Attribute not found: $name", attribute)
    assertEquals(expectedValue, attribute?.data)
}
