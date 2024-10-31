package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.fakes.LogEventData
import io.embrace.android.embracesdk.fakes.SpanEventData
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute.Failure.fromErrorCode
import io.embrace.android.embracesdk.internal.arch.schema.FixedAttribute
import io.embrace.android.embracesdk.internal.arch.schema.KeySpan
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.arch.schema.toPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.StatusCode
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

fun EmbraceSpanData.assertIsKeySpan(): Unit = assertHasEmbraceAttribute(KeySpan)

fun EmbraceSpanData.assertNotKeySpan(): Unit = assertDoesNotHaveEmbraceAttribute(KeySpan)

fun EmbraceSpanData.assertIsPrivateSpan(): Unit = assertHasEmbraceAttribute(PrivateSpan)

fun EmbraceSpanData.assertNotPrivateSpan(): Unit = assertDoesNotHaveEmbraceAttribute(PrivateSpan)

/**
 * Assert [EmbraceSpanData] has the [FixedAttribute] defined by [fixedAttribute]
 */
fun EmbraceSpanData.assertHasEmbraceAttribute(fixedAttribute: FixedAttribute) {
    assertTrue(hasFixedAttribute(fixedAttribute))
}

fun EmbraceSpanData.assertDoesNotHaveEmbraceAttribute(fixedAttribute: FixedAttribute) {
    assertFalse(attributes[fixedAttribute.key.name]?.equals(fixedAttribute.value) ?: false)
}

/**
 * Assert [EmbraceSpanData] has ended with the error defined by [errorCode]
 */
fun EmbraceSpanData.assertError(errorCode: ErrorCode) {
    assertEquals(StatusCode.ERROR, status)
    assertHasEmbraceAttribute(errorCode.fromErrorCode())
}

/**
 * Assert [EmbraceSpanData] has ended successfully
 */
fun EmbraceSpanData.assertSuccessful() {
    assertNotEquals(StatusCode.ERROR, status)
    assertNull(attributes[ErrorCodeAttribute.Failure.key.name])
}

fun Span.assertIsTypePerformance(): Unit = assertIsType(EmbType.Performance.Default)

fun Span.assertIsType(telemetryType: TelemetryType): Unit = assertHasEmbraceAttribute(telemetryType)

fun Span.assertIsKeySpan(): Unit = assertHasEmbraceAttribute(KeySpan)

fun Span.assertNotKeySpan(): Unit = assertDoesNotHaveEmbraceAttribute(KeySpan)

fun Span.assertIsPrivateSpan(): Unit = assertHasEmbraceAttribute(PrivateSpan)

fun Span.assertNotPrivateSpan(): Unit = assertDoesNotHaveEmbraceAttribute(PrivateSpan)

fun Span.assertHasEmbraceAttribute(fixedAttribute: FixedAttribute) {
    assertTrue(checkNotNull(attributes).contains(fixedAttribute.toPayload()))
}

fun Span.assertDoesNotHaveEmbraceAttribute(fixedAttribute: FixedAttribute) {
    assertFalse(checkNotNull(attributes).contains(fixedAttribute.toPayload()))
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
