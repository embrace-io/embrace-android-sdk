package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.destination.LogEventData
import io.embrace.android.embracesdk.arch.destination.StartSpanData
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.EmbraceAttribute
import io.embrace.android.embracesdk.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.arch.schema.KeySpan
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.StatusCode
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Assert.assertFalse

/**
 * Assert [EmbraceSpanData] is of type [EmbType.Performance.Default]
 */
internal fun EmbraceSpanData.assertIsTypePerformance() = assertIsType(EmbType.Performance.Default)

/**
 * Assert [EmbraceSpanData] is of type [telemetryType]
 */
internal fun EmbraceSpanData.assertIsType(telemetryType: TelemetryType) = assertHasEmbraceAttribute(telemetryType)

internal fun EmbraceSpanData.assertIsKeySpan() = assertHasEmbraceAttribute(KeySpan)

internal fun EmbraceSpanData.assertNotKeySpan() = assertDoesNotHaveEmbraceAttribute(KeySpan)

/**
 * Assert [EmbraceSpanData] has the [EmbraceAttribute] defined by [embraceAttribute]
 */
internal fun EmbraceSpanData.assertHasEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertEquals(embraceAttribute.attributeValue, attributes[embraceAttribute.otelAttributeName()])
}

internal fun EmbraceSpanData.assertDoesNotHaveEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertFalse(attributes[embraceAttribute.otelAttributeName()]?.equals(embraceAttribute.attributeValue) ?: false)
}

/**
 * Assert [EmbraceSpanData] has ended with the error defined by [errorCode]
 */
internal fun EmbraceSpanData.assertError(errorCode: ErrorCode) {
    assertEquals(StatusCode.ERROR, status)
    assertHasEmbraceAttribute(errorCode.fromErrorCode())
}

/**
 * Assert [EmbraceSpanData] has ended successfully
 */
internal fun EmbraceSpanData.assertSuccessful() {
    assertEquals(StatusCode.OK, status)
    assertNull(attributes[ErrorCodeAttribute.Failure.otelAttributeName()])
}

/**
 * Assert [StartSpanData] is of type [telemetryType]
 */
internal fun StartSpanData.assertIsType(telemetryType: TelemetryType) = assertHasEmbraceAttribute(telemetryType)

/**
 * Assert [StartSpanData] has the [EmbraceAttribute] defined by [embraceAttribute]
 */
internal fun StartSpanData.assertHasEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertEquals(embraceAttribute.attributeValue, attributes[embraceAttribute.otelAttributeName()])
}

/**
 * Assert [LogEventData] is of type [telemetryType]
 */
internal fun LogEventData.assertIsType(telemetryType: TelemetryType) = assertHasEmbraceAttribute(telemetryType)

/**
 * Assert [LogEventData] has the [EmbraceAttribute] defined by [embraceAttribute]
 */
internal fun LogEventData.assertHasEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertEquals(embraceAttribute.attributeValue, attributes[embraceAttribute.otelAttributeName()])
}
