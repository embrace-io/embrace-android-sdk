package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeValue
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Validate a state transition event. The value type is expected to be recorded only if [newStateValue] is a system value.
 */
fun <T : Any> SpanEvent.assertStateTransition(
    timestampMs: Long,
    newStateValue: T,
    notInSession: Int = 0,
    droppedByInstrumentation: Int = 0,
    transitionAttributes: Map<String, String> = emptyMap(),
) {
    assertEquals("transition", name)
    assertEquals(timestampMs.millisToNanos(), timestampNanos)
    assertNewStateValue(newStateValue)
    with(checkNotNull(attributes)) {
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

/**
 * Validate that a transition event's new value is the system value [value], recorded along with its value type.
 */
fun SpanEvent.assertSystemNewStateValue(value: Any): Unit =
    stateAttributes().assertSystemStateValue(value, EmbStateTransitionAttributes.EMB_STATE_NEW_VALUE)

/**
 * Validate that a transition event's new value is the non-system value [value], recorded with no value type.
 */
fun SpanEvent.assertNonSystemNewStateValue(value: Any): Unit =
    stateAttributes().assertNonSystemStateValue(value, EmbStateTransitionAttributes.EMB_STATE_NEW_VALUE)

/**
 * Validate that a transition event's new value is [value], recorded with its value type only if it is a system value.
 */
internal fun SpanEvent.assertNewStateValue(value: Any): Unit =
    stateAttributes().assertStateValue(value, EmbStateTransitionAttributes.EMB_STATE_NEW_VALUE)

private fun SpanEvent.stateAttributes(): Map<String, String> = checkNotNull(attributes).toMap()
