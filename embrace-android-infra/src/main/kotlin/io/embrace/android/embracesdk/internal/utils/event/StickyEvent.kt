package io.embrace.android.embracesdk.internal.utils.event

/**
 * Marker for event types that describe state rather than an occurrence. The most recent one is retained and replayed to a handler
 * registered afterwards, if applicable to the type it registered against.
 *
 * The type carrying this marker names one axis of state, so its subtypes must be refinements or alternative values along it; emitting
 * any of them replaces whatever the axis held. Carrying it on a type spanning two independent states makes them displace each other.
 */
interface StickyEvent
