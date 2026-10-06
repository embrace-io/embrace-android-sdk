package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * The number of keys an [EventBus] holds before its first growth, covering every key the SDK declares so that growth is only a safety net.
 */
internal const val INITIAL_CAPACITY = 16

/**
 * A keyed event bus where an event emitted against an [EventKey] reaches only the handlers registered against that key, on the
 * calling thread, in the order they were registered.
 *
 * State travels separately, against a [StateKey]: a value emitted against one is retained and replayed to handlers registered
 * against that key afterwards.
 *
 * A [BusPoll] travels the other way, against a [PollKey]: it is sent to every [PollHandler] registered against its key, and their
 * results are gathered back to the caller.
 */
class EventBus(private val internalErrorHandler: InternalErrorHandler) {

    /**
     * Guards every change to [registrations], and every replacement of [retainedStateValues].
     */
    private val lock = Any()

    /**
     * The handlers registered against each key, indexed by [BusKey.id], written and replaced only under [lock].
     *
     * This `AtomicReferenceArray` is heterogeneous and each slot's array is created with the element type of what it holds
     * (`Array<EventHandler>`, `Array<PollHandler>`), so that it can be cast whole by [registeredFor].
     */
    @Volatile
    private var registrations = AtomicReferenceArray<Array<*>?>(INITIAL_CAPACITY)

    /**
     * The value most recently emitted against each [StateKey], indexed by [BusKey.id], replaced only by [retainStateUnderLock].
     *
     * Nothing is ever evicted, so a retained value must not reference anything shorter lived than the process - an `Activity` or
     * `View` reachable from one leaks for the rest of it.
     */
    @Volatile
    private var retainedStateValues = AtomicReferenceArray<Any?>(INITIAL_CAPACITY)

    /**
     * Set while [retainStateUnderLock] copies [retainedStateValues], so that a value written outside [lock] during the copy is written
     * again under it. We use this marker to avoid [emitState] needing to hold [lock] every time it writes an emitted value to
     * [retainedStateValues].
     */
    @Volatile
    private var currentlyGrowingRetainedStates = false

    /**
     * Handlers already reported as having thrown, so that each is reported once. Identity keyed, as registration is.
     */
    private val loggedFailures = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()))

    /**
     * Registers [handler] against [key]. Registering a handler already registered against [key] does nothing.
     */
    fun <E : Any> addHandler(key: EventKey<E>, handler: EventHandler<E>) {
        addTo<EventHandler<*>>(key, handler)
    }

    /**
     * Registers [handler] against [key], replaying the value retained against it before returning. Registering a handler already
     * registered against [key] does nothing.
     */
    fun <E : Any> addStateHandler(key: StateKey<E>, handler: EventHandler<E>) {
        if (addTo<EventHandler<*>>(key, handler)) {
            replayState(key, handler)
        }
    }

    /**
     * Registers [handler] against [key]. Registering a handler already registered against [key] does nothing.
     */
    fun <V : Any> addPollHandler(key: PollKey<V>, handler: PollHandler<V>) {
        addTo<PollHandler<*>>(key, handler)
    }

    /**
     * Unregisters [handler] from [key], forgetting any failure reported for it so that registering it again, reports again.
     */
    fun <E : Any> removeHandler(key: EventKey<E>, handler: EventHandler<E>) {
        if (removeFrom<EventHandler<*>>(key, handler)) {
            loggedFailures.remove(handler)
        }
    }

    /**
     * Unregisters [handler] from [key], forgetting any failure reported for it so that registering it again, reports again.
     */
    fun <E : Any> removeStateHandler(key: StateKey<E>, handler: EventHandler<E>) {
        if (removeFrom<EventHandler<*>>(key, handler)) {
            loggedFailures.remove(handler)
        }
    }

    /**
     * Unregisters [handler] from [key], forgetting any failure reported for it so that registering it again, reports again.
     */
    fun <V : Any> removePollHandler(key: PollKey<V>, handler: PollHandler<V>) {
        if (removeFrom<PollHandler<*>>(key, handler)) {
            loggedFailures.remove(handler)
        }
    }

    /**
     * Emits [event] to every handler registered against [key], on the calling thread, retaining nothing.
     */
    fun <E : Any> emit(key: EventKey<E>, event: E) {
        val registered = registeredFor<EventHandler<*>>(key.id) ?: return
        deliverTo(registered, event)
    }

    /**
     * Emits [value] as the current value of the state [key] names, retaining it for replay and delivering it to the handlers
     * registered against [key].
     *
     * Emitting against a [key] supersedes whatever it held, so alternative values of one state share a key.
     */
    fun <E : Any> emitState(key: StateKey<E>, value: E) {
        // retained before delivery, so a handler registering concurrently sees the value twice rather than not at all
        val current = retainedStateValues
        if (key.id < current.length()) {
            current.set(key.id, value)

            // a copy in progress, or completed since current was read, may not have carried the value across
            if (currentlyGrowingRetainedStates || retainedStateValues !== current) {
                retainStateUnderLock(key.id, value)
            }
        } else {
            retainStateUnderLock(key.id, value)
        }

        val registered = registeredFor<EventHandler<*>>(key.id) ?: return
        deliverTo(registered, value)
    }

    /**
     * Sends [poll] to every handler registered against its key, on the calling thread, and returns their results. A handler that
     * throws contributes no result.
     */
    fun <V : Any> poll(poll: BusPoll<V>): List<PollResult<V>> {
        val registered = registeredFor<PollHandler<*>>(poll.key.id) ?: return emptyList()
        val results = ArrayList<PollResult<V>>(registered.size)
        for (handler in registered) {
            @Suppress("UNCHECKED_CAST") // addPollHandler pairs a key with handlers of that key's own type
            val typed = handler as PollHandler<V>
            try {
                results.add(typed.onPoll(poll))
            } catch (failure: Exception) {
                reportFailure(handler, failure)
            }
        }
        return results
    }

    /**
     * Replays the value retained against [key], if there is one, to a newly registered [handler].
     */
    private fun <E : Any> replayState(key: StateKey<E>, handler: EventHandler<E>) {
        val current = retainedStateValues
        val retained = (if (key.id < current.length()) current.get(key.id) else null) ?: return

        @Suppress("UNCHECKED_CAST") // emitState is the only writer, and it pairs a key with a value of that key's own type
        val value = retained as E
        try {
            handler.onEvent(value)
        } catch (failure: Exception) {
            reportFailure(handler, failure)
        }
    }

    private fun <E : Any> deliverTo(registered: Array<EventHandler<*>>, event: E) {
        for (handler in registered) {
            @Suppress("UNCHECKED_CAST") // addHandler and addStateHandler pair a key with handlers of that key's own type
            val typed = handler as EventHandler<E>
            try {
                typed.onEvent(event)
            } catch (failure: Exception) {
                reportFailure(handler, failure)
            }
        }
    }

    private fun reportFailure(registrant: Any, failure: Exception) {
        if (loggedFailures.add(registrant)) {
            internalErrorHandler.trackInternalError(InternalErrorType.EventBusHandlerFail, failure)
        }
    }

    /**
     * The array registered against [id], cast whole to the element type it was created with.
     */
    @Suppress("UNCHECKED_CAST") // checked against the array's element class, which addTo and removeFrom create as T
    private inline fun <reified T : Any> registeredFor(id: Int): Array<T>? {
        val current = registrations
        return (if (id < current.length()) current.get(id) else null) as Array<T>?
    }

    /**
     * Adds [registrant] to those registered against [key], replacing the array rather than changing it so that a dispatch already
     * holding it is undisturbed, and reports whether it was not already registered.
     *
     * Callers pin [T] to the registered interface, as the slot's array is created with that element type and a narrower one would
     * reject the next registrant.
     */
    private inline fun <reified T : Any> addTo(key: BusKey, registrant: T): Boolean {
        synchronized(lock) {
            var current = registrations
            if (key.id >= current.length()) {
                current = grownToHold(current, key.id)
                registrations = current
            }

            @Suppress("UNCHECKED_CAST") // checked against the array's element class, which this and removeFrom create as T
            val existing = current.get(key.id) as Array<T>?
            if (existing == null) {
                current.set(key.id, arrayOf(registrant))
                return true
            }
            if (existing.any { it === registrant }) {
                return false
            }
            current.set(key.id, existing + registrant)
            return true
        }
    }

    /**
     * Removes [registrant] from those registered against [key], replacing the array rather than changing it so that a dispatch
     * already holding it is undisturbed, and reports whether it was registered.
     */
    private inline fun <reified T : Any> removeFrom(key: BusKey, registrant: T): Boolean {
        synchronized(lock) {
            val existing = registeredFor<T>(key.id) ?: return false
            if (existing.none { it === registrant }) {
                return false
            }
            val remaining = existing.filterNot { it === registrant }
            registrations.set(key.id, if (remaining.isEmpty()) null else remaining.toTypedArray())
            return true
        }
    }

    /**
     * Writes [value] against [id] under [lock], first growing [retainedStateValues] to hold [id] if it can't.
     */
    private fun retainStateUnderLock(id: Int, value: Any) {
        synchronized(lock) {
            var current = retainedStateValues
            if (id >= current.length()) {
                currentlyGrowingRetainedStates = true
                current = grownToHold(current, id)
                retainedStateValues = current
                currentlyGrowingRetainedStates = false
            }
            current.set(id, value)
        }
    }

    /**
     * Copies [source] into a new array large enough to hold [id].
     */
    private fun <T> grownToHold(source: AtomicReferenceArray<T>, id: Int): AtomicReferenceArray<T> {
        val grown = AtomicReferenceArray<T>(id.takeHighestOneBit() shl 1)
        for (i in 0 until source.length()) {
            grown.lazySet(i, source.get(i))
        }
        return grown
    }
}
