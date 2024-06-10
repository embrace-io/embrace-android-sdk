package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.truncatedStacktrace
import io.embrace.android.embracesdk.opentelemetry.embExceptionHandling
import io.embrace.android.embracesdk.opentelemetry.exceptionMessage
import io.embrace.android.embracesdk.opentelemetry.exceptionStacktrace
import io.embrace.android.embracesdk.opentelemetry.exceptionType
import org.junit.Assert.assertEquals
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
) {
    assertNotNull(logReceived)
    logReceived?.let { log ->
        assertEquals(expectedEmbType, log.attributes?.find { it.key == "emb.type" }?.data)
        assertEquals(expectedMessage, log.body)
        assertEquals(expectedSeverityNumber, log.severityNumber)
        assertEquals(expectedSeverityText, log.severityText)
        assertEquals(expectedTimeMs * 1000000, log.timeUnixNano)
        expectedType?.let { assertAttribute(log, embExceptionHandling.name, it) }
        expectedExceptionName?.let {
            assertAttribute(log, exceptionType.key, expectedExceptionName)
        }
        expectedExceptionMessage?.let {
            assertAttribute(log, exceptionMessage.key, expectedExceptionMessage)
        }
        expectedStacktrace?.let {
            val serializedStack = EmbraceSerializer().truncatedStacktrace(it.toTypedArray())
            assertAttribute(log, exceptionStacktrace.key, serializedStack)
        }
        expectedProperties?.forEach { (key, value) ->
            assertAttribute(log, key, value.toString())
        }
    }
}

private fun assertAttribute(log: Log, name: String, expectedValue: String) {
    val attribute = log.attributes?.find { it.key == name }
    assertNotNull(attribute)
    assertEquals(expectedValue, attribute?.data)
}
