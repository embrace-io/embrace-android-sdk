
package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.fakes.fromErrorCode
import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

fun Span.assertIsTypePerformance(): Unit = assertIsType(EmbType.Performance.Default)

fun Span.assertIsType(telemetryType: EmbType): Unit = assertHasEmbraceAttribute(telemetryType)

fun Span.assertIsPrivateSpan(): Unit = assertHasEmbraceAttribute(PrivateSpan)

fun Span.assertNotPrivateSpan(): Unit = assertDoesNotHaveEmbraceAttribute(PrivateSpan)

/**
 * Return as a [Attribute] representation, to be used for Embrace payloads
 */
fun EmbraceAttribute.toPayload(): Attribute = Attribute(key, value)

fun Span.assertHasEmbraceAttribute(embraceAttribute: EmbraceAttribute) {
    with(checkNotNull(attributes)) {
        val attrPayload = embraceAttribute.toPayload()
        assertTrue("Span with name $name does not contain attribute '${attrPayload.key}'", any { it.key == attrPayload.key })
        assertEquals(attrPayload.data, single { it.key == attrPayload.key }.data)
    }
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
    assertEquals(0, checkNotNull(attributes).filter { it.key == ErrorCodeAttribute.Failure.key }.size)
}
