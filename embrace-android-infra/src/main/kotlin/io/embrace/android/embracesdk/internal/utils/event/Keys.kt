package io.embrace.android.embracesdk.internal.utils.event

import java.util.concurrent.atomic.AtomicInteger

/**
 * Superclass for all keys used for event dispatching in the `EventBus`, each representing a single type-safe channel of event broadcasting.
 * Each `BusKey` has a unique `id` and no two keys in a single process will share a key (though the keys are not stable between two
 * processes).
 */
sealed class BusKey {
    /**
     * The unique ID for this `BusKey`. IDs are generated sequentially (starting with `0`) and no two `BusKey`s can share an ID. They
     * can be treated like ordinals within an `enum class`.
     */
    val id = nextId.getAndIncrement()

    override fun hashCode(): Int = id
    override fun equals(other: Any?): Boolean = other is BusKey && id == other.id

    private companion object {
        @JvmStatic
        val nextId = AtomicInteger(0)
    }
}

/**
 * Identifies one channel of events on an [EventBus], delivered only to the handlers registered against the same key.
 *
 * A key is identified by the instance, so one is expected to be declared once and shared by everything emitting or handling those
 * events.
 */
class EventKey<E : Any> : BusKey()

/**
 * Identifies one axis of state on an [EventBus], whose most recent value is retained and replayed to a handler registered against the
 * key after the value was emitted.
 *
 * A key is identified by the instance, so one is expected to be declared once and shared by everything reading or writing that state.
 */
class StateKey<E : Any> : BusKey()

/**
 * Identifies one channel for polling between modules. [BusPoll]s are channel typed requests for data that have results.
 *
 * A key is identified by the instance, so each one is expected to be declared once and shared by everything that needs to share a poll
 * channel.
 */
class PollKey<V : Any> : BusKey()
