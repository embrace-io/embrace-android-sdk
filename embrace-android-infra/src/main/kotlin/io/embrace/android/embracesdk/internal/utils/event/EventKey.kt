package io.embrace.android.embracesdk.internal.utils.event

/**
 * Identifies one channel of events on an [EventBus], delivered only to the handlers registered against the same key.
 *
 * A key is identified by the instance, so one is expected to be declared once and shared by everything emitting or handling those
 * events.
 */
class EventKey<E : Any>
