package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeValue
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

fun <T: Any> SpanEvent.assertStateTransition(
    timestampMs: Long,
    newStateValue: T,
    notInSession: Int = 0,
    droppedByInstrumentation: Int = 0,
    transitionAttributes: Map<String, String> = emptyMap(),
) {
    assertEquals("transition", name)
    assertEquals(timestampMs.millisToNanos(), timestampNanos)
    with(checkNotNull(attributes)) {
        assertTrue(hasEmbraceAttributeValue(EmbStateTransitionAttributes.EMB_STATE_NEW_VALUE, newStateValue))
        if (notInSession > 0) {
            assertTrue(hasEmbraceAttributeValue(EmbStateTransitionAttributes.EMB_STATE_NOT_IN_SESSION, notInSession.toString()))
        } else {
            assertFalse(hasEmbraceAttributeKey(EmbStateTransitionAttributes.EMB_STATE_NOT_IN_SESSION))
        }

        if (droppedByInstrumentation > 0) {
            assertTrue(hasEmbraceAttributeValue(EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION, droppedByInstrumentation.toString()))
        } else {
            assertFalse(hasEmbraceAttributeKey(EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION))
        }

        transitionAttributes.forEach { (key, value) ->
            assertTrue("Expected attribute $key=$value on transition event", hasEmbraceAttributeValue(key, value))
        }
    }
}
