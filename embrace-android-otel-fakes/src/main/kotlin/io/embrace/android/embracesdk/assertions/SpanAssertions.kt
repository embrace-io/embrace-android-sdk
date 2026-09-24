package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeKey
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.semconv.EmbSpanAttributes
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes.EMB_STATE_INITIAL_VALUE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

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

fun Span.assertPreviousSessionPart(previousSessionPartSpan: Span, previousSessionPartId: String) {
    findLinkOfType(LinkType.PreviousSessionPart).validatePreviousSessionPartLink(previousSessionPartSpan, previousSessionPartId)
}

fun Span.assertNoPreviousSessionPart() =
    links?.filter { it.attributes?.toMap()?.containsKey(LinkType.PreviousSessionPart.key) == false }?.size == 0

fun Span.findLinksOfType(type: LinkType) = links?.filter { it.attributes?.hasEmbraceAttribute(type) == true }

fun Span.findLinkOfType(type: LinkType): Link = checkNotNull(findLinksOfType(type)?.single())

fun Span.findCustomLinks() = links?.filter { it.attributes?.any { attr -> attr.key == EmbSpanAttributes.EMB_LINK_TYPE } == false }

fun Span.hasLinkToEmbraceSpan(linkedSpan: Span, type: LinkType): Boolean =
    findLinksOfType(type)?.any { it.isLinkedToSpan(linkedSpan, false) } == true


/**
 * Validates a Navigation State session part span with the given [newStateValues] representing the names of the screens derived from the
 * app. Based on whether the state is initialized when the session part started, it will look for the appropriate [Screen] as its
 * initial value, and [Screen.Backgrounded] as its ending value, with the appropriate state type attributes.
 */
fun Span.assertNavigationStateSpan(
    stateUninitialized: Boolean = true,
    transitionTimesMs: List<Long> = listOf(),
    newStateValues: List<String> = listOf(),
) {
    val startStateValue = if (stateUninitialized) {
        Screen.Initializing
    } else {
        Screen.Backgrounded
    }
    assertStateSpan(
        initialValue = startStateValue,
        transitionTimesMs = transitionTimesMs,
        newStateValues = newStateValues.map { Screen.Named(it) } + Screen.Backgrounded,
    )
}

/**
 * Validate that a state span has the given initial value and transition events with the given times with the associated state values.
 */
fun Span.assertStateSpan(
    initialValue: Any,
    transitionTimesMs: List<Long> = listOf(),
    newStateValues: List<Any> = listOf(),
) {
    assertInitialStateValue(initialValue)
    with(checkNotNull(events)) {
        assertEquals(transitionTimesMs.size, size)
        transitionTimesMs.indices.forEach {
            this[it].assertStateTransition(
                timestampMs = transitionTimesMs[it],
                newStateValue = newStateValues[it],
            )
        }
    }
}

/**
 * Validate that a state span's initial value is the system value [value], recorded along with its value type.
 */
fun Span.assertSystemInitialStateValue(value: Any): Unit = stateSpanAttributes().assertSystemStateValue(value, EMB_STATE_INITIAL_VALUE)

/**
 * Validate that a state span's initial value is the non-system value [value], recorded with no value type.
 */
fun Span.assertNonSystemInitialStateValue(value: Any): Unit = stateSpanAttributes().assertNonSystemStateValue(value, EMB_STATE_INITIAL_VALUE)

/**
 * Validate that a state span's initial value is [value], recorded with its value type only if it is a system value.
 */
internal fun Span.assertInitialStateValue(value: Any): Unit = stateSpanAttributes().assertStateValue(value, EMB_STATE_INITIAL_VALUE)

private fun Span.stateSpanAttributes(): Map<String, String> {
    assertIsType(EmbType.State)
    return checkNotNull(attributes).toMap()
}

fun List<Attribute>?.assertSdkInitSectionDurationsRecorded() {
    expectedSdkInitSections.forEach { section ->
        assertNotNull(
            "duration for init section `$section` not found",
            this?.findAttributeValue("$section-duration-ms")
        )
    }
}

/**
 * Sections known to run within the SDK init flow during integration tests.
 */
private val expectedSdkInitSections = listOf(
    "modules-init",
    "persisted-config-load",
    "prefs-first-read",
    "span-service-init",
    "essential-service-init",
    "delivery-init",
    "payload-source-init",
    "otel-tracer-init",
    "post-init",
    "load-instrumentation",
    "post-services-setup",
)
