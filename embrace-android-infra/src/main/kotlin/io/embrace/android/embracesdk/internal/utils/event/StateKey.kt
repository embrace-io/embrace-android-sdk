package io.embrace.android.embracesdk.internal.utils.event

/**
 * Identifies one axis of state on an [EventBus], whose most recent value is retained and replayed to a handler registered against the
 * key after the value was emitted.
 *
 * A key is identified by the instance, so one is expected to be declared once and shared by everything reading or writing that state.
 */
class StateKey<E : Any>
