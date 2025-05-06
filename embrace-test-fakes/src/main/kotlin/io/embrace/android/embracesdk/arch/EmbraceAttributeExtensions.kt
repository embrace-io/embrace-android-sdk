package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.fakes.LogEventData
import io.embrace.android.embracesdk.fakes.SpanEventData
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.otel.schema.ErrorCodeAttribute.Failure.fromErrorCode
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.schema.TelemetryType
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.toStatus
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * Assert [EmbraceSpanData] is of type [EmbType.Performance.Default]
 */
fun EmbraceSpanData.assertIsTypePerformance(): Unit = assertIsType(EmbType.Performance.Default)

/**
 * Assert [EmbraceSpanData] is of type [telemetryType]
 */
fun EmbraceSpanData.assertIsType(telemetryType: TelemetryType): Unit = assertHasEmbraceAttribute(telemetryType)

fun EmbraceSpanData.assertIsPrivateSpan(): Unit = assertHasEmbraceAttribute(PrivateSpan)

fun EmbraceSpanData.assertNotPrivateSpan(): Unit = assertDoesNotHaveEmbraceAttribute(PrivateSpan)

/**
 * Assert [EmbraceSpanData] has the [EmbraceAttribute] defined by [embraceAttribute]
 */
fun EmbraceSpanData.assertHasEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertTrue(hasEmbraceAttribute(embraceAttribute))
}

fun EmbraceSpanData.assertDoesNotHaveEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertFalse(attributes[embraceAttribute.key.name]?.equals(embraceAttribute.value) ?: false)
}

/**
 * Assert [EmbraceSpanData] has ended with the error defined by [errorCode]
 */
fun EmbraceSpanData.assertError(errorCode: ErrorCode) {
    assertEquals(StatusCode.Error(null).toStatus(), status.toStatus())
    assertHasEmbraceAttribute(errorCode.fromErrorCode())
}

/**
 * Assert [EmbraceSpanData] has ended successfully
 */
fun EmbraceSpanData.assertSuccessful() {
    assertNotEquals(StatusCode.Error(null), status)
    assertNull(attributes[ErrorCodeAttribute.Failure.key.name])
}

fun Span.assertIsTypePerformance(): Unit = assertIsType(EmbType.Performance.Default)

fun Span.assertIsType(telemetryType: TelemetryType): Unit = assertHasEmbraceAttribute(telemetryType)

fun Span.assertIsPrivateSpan(): Unit = assertHasEmbraceAttribute(PrivateSpan)

fun Span.assertNotPrivateSpan(): Unit = assertDoesNotHaveEmbraceAttribute(PrivateSpan)

/**
 * Return as a [Attribute] representation, to be used used for Embrace payloads
 */
fun EmbraceAttribute.toPayload(): Attribute = Attribute(key.name, value)

fun Span.assertHasEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertTrue(checkNotNull(attributes).contains(embraceAttribute.toPayload()))
}

fun Span.assertDoesNotHaveEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    assertFalse(checkNotNull(attributes).contains(embraceAttribute.toPayload()))
}

fun Span.assertError(errorCode: ErrorCode) {
    assertEquals(Span.Status.ERROR, status)
    assertHasEmbraceAttribute(errorCode.fromErrorCode())
}

fun Span.assertSuccessful() {
    assertNotEquals(Span.Status.ERROR, status)
    assertEquals(0, checkNotNull(attributes).filter { it.key == ErrorCodeAttribute.Failure.key.name }.size)
}

/**
 * Assert [SpanEventData] is of type [telemetryType]
 */
fun SpanEventData.assertIsType(
    telemetryType: TelemetryType,
): Unit = assertEquals(telemetryType, schemaType.telemetryType)

/**
 * Assert [LogEventData] is of type [telemetryType]
 */
fun LogEventData.assertIsType(
    telemetryType: TelemetryType,
): Unit = assertEquals(telemetryType, schemaType.telemetryType)
