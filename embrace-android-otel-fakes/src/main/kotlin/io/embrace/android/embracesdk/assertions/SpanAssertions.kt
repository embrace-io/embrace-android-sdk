package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttributeValue
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.semconv.EmbSpanAttributes
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes.EMB_STATE_INITIAL_VALUE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Finds the first Span Event matching the given [EmbType]
 */
fun Span.findEventOfType(telemetryType: EmbType): SpanEvent {
    val sanitizedEvents = checkNotNull(events) {
        "No events found in span"
    }
    return checkNotNull(sanitizedEvents.single { it.hasEmbraceAttribute(telemetryType) }) {
        "Event not found: $name"
    }
}

/**
 * Finds the Span Events matching the given [EmbType]
 */
fun Span.findEventsOfType(telemetryType: EmbType): List<SpanEvent> {
    val sanitizedEvents = checkNotNull(events) {
        "No events found in span"
    }
    return checkNotNull(sanitizedEvents.filter { checkNotNull(it.attributes).hasEmbraceAttributeKey(telemetryType.key) }) {
        "Events not found: $name"
    }
}

fun Span.assertPreviousSession(previousSessionSpan: Span, previousSessionId: String) {
    findLinkOfType(LinkType.PreviousSession).validatePreviousSessionLink(previousSessionSpan, previousSessionId)
}

fun Span.assertNoPreviousSession() =
    links?.filter { it.attributes?.toMap()?.containsKey(LinkType.PreviousSession.key) == false }?.size == 0

fun Span.findLinksOfType(type: LinkType) = links?.filter { it.attributes?.hasEmbraceAttribute(type) == true }

fun Span.findLinkOfType(type: LinkType): Link = checkNotNull(findLinksOfType(type)?.single())

fun Span.findCustomLinks() = links?.filter { it.attributes?.any { attr -> attr.key == EmbSpanAttributes.EMB_LINK_TYPE } == false }

fun Span.hasLinkToEmbraceSpan(linkedSpan: Span, type: LinkType): Boolean =
    findLinksOfType(type)?.any { it.isLinkedToSpan(linkedSpan, false) } == true


/**
 * Validate a Navigation State span
 */
fun Span.assertNavigationStateSpan(
    stateUninitialized: Boolean = true,
    transitionTimesMs: List<Long> = listOf(),
    newStateValues: List<String> = listOf(),
) = assertStateSpan(
    startStateValue = if (stateUninitialized) {
        "Initializing"
    } else {
        "Backgrounded"
    },
    transitionTimesMs = transitionTimesMs,
    newStateValues = newStateValues
)

/**
 * Validates a state span's initial value attribute and all transition events.
 */
fun Span.assertStateSpan(
    startStateValue: String,
    transitionTimesMs: List<Long> = listOf(),
    newStateValues: List<String> = listOf(),
) {
    assertTrue(hasEmbraceAttributeValue(EMB_STATE_INITIAL_VALUE, startStateValue))
    with(checkNotNull(events)) {
        assertEquals(transitionTimesMs.size, size)
        if (isNotEmpty()) {
            (0..<transitionTimesMs.size - 1).forEach {
                this[it].assertStateTransition(
                    timestampMs = transitionTimesMs[it],
                    newStateValue = newStateValues[it],
                )
            }
            last().assertStateTransition(
                timestampMs = transitionTimesMs.last(),
                newStateValue = "Backgrounded",
            )
        }
    }
}
