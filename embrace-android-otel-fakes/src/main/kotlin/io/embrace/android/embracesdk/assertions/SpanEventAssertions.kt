package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.arch.attrs.embStateDroppedByInstrumentation
import io.embrace.android.embracesdk.internal.arch.attrs.embStateNewValue
import io.embrace.android.embracesdk.internal.arch.attrs.embStateNotInSession
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeValue
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

fun <T: Any> SpanEvent.assertStateTransition(
    timestampMs: Long,
    newStateValue: T,
    notInSession: Int = 0,
    droppedByInstrumentation: Int = 0,
) {
    assertEquals("transition", name)
    assertEquals(timestampMs.millisToNanos(), timestampNanos)
    with(checkNotNull(attributes)) {
        assertTrue(hasEmbraceAttributeValue(embStateNewValue, newStateValue))
        if (notInSession > 0) {
            assertTrue(hasEmbraceAttributeValue(embStateNotInSession, notInSession.toString()))
        } else {
            assertFalse(hasEmbraceAttributeKey(embStateNotInSession))
        }

        if (droppedByInstrumentation > 0) {
            assertTrue(hasEmbraceAttributeValue(embStateDroppedByInstrumentation, droppedByInstrumentation.toString()))
        } else {
            assertFalse(hasEmbraceAttributeKey(embStateDroppedByInstrumentation))
        }
    }
}
