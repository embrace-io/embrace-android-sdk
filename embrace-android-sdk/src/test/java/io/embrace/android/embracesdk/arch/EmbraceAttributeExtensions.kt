package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.destination.LogEventData
import io.embrace.android.embracesdk.arch.destination.StartSpanData
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.EmbraceAttribute
import io.embrace.android.embracesdk.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.arch.schema.KeySpan
import io.embrace.android.embracesdk.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

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

internal fun EmbraceSpanData.assertIsPrivateSpan() = assertHasEmbraceAttribute(PrivateSpan)

internal fun EmbraceSpanData.assertNotPrivateSpan() = assertDoesNotHaveEmbraceAttribute(PrivateSpan)

/**
 * Assert [EmbraceSpanData] has the [EmbraceAttribute] defined by [embraceAttribute]
 */
internal fun EmbraceSpanData.assertHasEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertTrue(hasEmbraceAttribute(embraceAttribute))
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

internal fun Span.assertIsTypePerformance() = assertIsType(EmbType.Performance.Default)

internal fun Span.assertIsType(telemetryType: TelemetryType) = assertHasEmbraceAttribute(telemetryType)

internal fun Span.assertIsKeySpan() = assertHasEmbraceAttribute(KeySpan)

internal fun Span.assertNotKeySpan() = assertDoesNotHaveEmbraceAttribute(KeySpan)

internal fun Span.assertIsPrivateSpan() = assertHasEmbraceAttribute(PrivateSpan)

internal fun Span.assertNotPrivateSpan() = assertDoesNotHaveEmbraceAttribute(PrivateSpan)

internal fun Span.assertHasEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertTrue(checkNotNull(attributes).contains(embraceAttribute.toAttributePayload()))
}

internal fun Span.assertDoesNotHaveEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertFalse(checkNotNull(attributes).contains(embraceAttribute.toAttributePayload()))
}

internal fun Span.assertError(errorCode: ErrorCode) {
    assertEquals(Span.Status.ERROR, status)
    assertHasEmbraceAttribute(errorCode.fromErrorCode())
}

internal fun Span.assertSuccessful() {
    assertEquals(Span.Status.OK, status)
    assertEquals(0, checkNotNull(attributes).filter { it.key == ErrorCodeAttribute.Failure.otelAttributeName() }.size)
}

/**
 * Assert [StartSpanData] is of type [telemetryType]
 */
internal fun StartSpanData.assertIsType(telemetryType: TelemetryType) = assertHasEmbraceAttribute(telemetryType)

/**
 * Assert [StartSpanData] has the [EmbraceAttribute] defined by [embraceAttribute]
 */
internal fun StartSpanData.assertHasEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertEquals(embraceAttribute.attributeValue, schemaType.attributes()[embraceAttribute.otelAttributeName()])
}

/**
 * Assert [LogEventData] is of type [telemetryType]
 */
internal fun LogEventData.assertIsType(telemetryType: TelemetryType) = assertHasEmbraceAttribute(telemetryType)

/**
 * Assert [LogEventData] has the [EmbraceAttribute] defined by [embraceAttribute]
 */
internal fun LogEventData.assertHasEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertEquals(embraceAttribute.attributeValue, schemaType.attributes()[embraceAttribute.otelAttributeName()])
}
