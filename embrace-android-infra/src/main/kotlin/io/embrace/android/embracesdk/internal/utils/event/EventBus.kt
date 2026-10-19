package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.BitSet
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * The number of keys an [EventBus] holds before its first growth, covering every key the SDK declares so that growth is only a safety net.
 */
internal const val INITIAL_CAPACITY = 16

/**
 * A keyed event bus where an event emitted against an [EventKey] reaches only the handlers registered against that key, on the
 * calling thread, in ascending order of the order each was registered with, and in the order they were registered within an order.
 *
 * State travels separately, against a [StateKey]: a value emitted against one is retained and replayed to handlers registered
 * against that key afterwards.
 */
class EventBus(private val internalErrorHandler: InternalErrorHandler) {

    /**
     * Guards every change to [registrations], and every replacement of [retainedStateValues].
     */
    private val lock = Any()

    /**
     * The handlers registered against each key, indexed by [BusKey.id], written and replaced only under [lock].
     */
    @Volatile
    private var registrations = AtomicReferenceArray<Registrations?>(INITIAL_CAPACITY)

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
     * Guards [ensuredProducers], held while a producer factory runs so that no caller returns before its key's producer exists.
     */
    private val producerLock = Any()

    /**
     * The ids of the keys whose producer factory has been run, written and read only under [producerLock].
     */
    private val ensuredProducers = BitSet()

    /**
     * Runs [factory] to create the producer of [key] unless a factory has already been run for it, returning only once it has.
     */
    fun ensureEventProducer(key: BusKey, factory: () -> Unit) {
        synchronized(producerLock) {
            if (ensuredProducers.get(key.id)) {
                return
            }
            ensuredProducers.set(key.id)
            try {
                factory()
            } catch (failure: Exception) {
                internalErrorHandler.trackInternalError(InternalErrorType.EventBusProducerFail, failure)
            }
        }
    }

    /**
     * Registers [handler] against [key] with [DEFAULT_ORDER]. Registering a handler already registered against [key] does nothing.
     */
    fun <E : Any> addHandler(key: EventKey<E>, handler: EventHandler<E>) {
        addHandler(key, DEFAULT_ORDER, handler)
    }

    /**
     * Registers [handler] against [key], to be delivered to after handlers with a lower [order]. Registering a handler already
     * registered against [key] does nothing.
     */
    fun <E : Any> addHandler(key: EventKey<E>, order: Int, handler: EventHandler<E>) {
        addTo(key, handler, order)
    }

    /**
     * Registers [handler] against [key], to be delivered to after handlers with a lower [order], replaying the value retained
     * against it before returning unless [replay] is false. Registering a handler already registered against [key] does nothing.
     */
    fun <E : Any> addStateHandler(
        key: StateKey<E>,
        replay: Boolean = true,
        order: Int = DEFAULT_ORDER,
        handler: EventHandler<E>,
    ) {
        if (addTo(key, handler, order) && replay) {
            replayState(key, handler)
        }
    }

    /**
     * Unregisters [handler] from [key], forgetting any failure reported for it so that registering it again, reports again.
     */
    fun <E : Any> removeHandler(key: EventKey<E>, handler: EventHandler<E>) {
        if (removeFrom(key, handler)) {
            loggedFailures.remove(handler)
        }
    }

    /**
     * Unregisters [handler] from [key], forgetting any failure reported for it so that registering it again, reports again.
     */
    fun <E : Any> removeStateHandler(key: StateKey<E>, handler: EventHandler<E>) {
        if (removeFrom(key, handler)) {
            loggedFailures.remove(handler)
        }
    }

    /**
     * Emits [event] to every handler registered against [key], on the calling thread, retaining nothing.
     */
    fun <E : Any> emit(key: EventKey<E>, event: E) {
        val registered = registrationsFor(key.id) ?: return
        deliverTo(registered.handlers, event)
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

        val registered = registrationsFor(key.id) ?: return
        deliverTo(registered.handlers, value)
    }

    /**
     * Returns the value most recently emitted against [key], or `null` if nothing has been emitted against it.
     */
    fun <E : Any> currentState(key: StateKey<E>): E? {
        val current = retainedStateValues

        @Suppress("UNCHECKED_CAST") // emitState is the only writer, and it pairs a key with a value of that key's own type
        return (if (key.id < current.length()) current.get(key.id) else null) as E?
    }

    /**
     * Replays the value retained against [key], if there is one, to a newly registered [handler].
     */
    private fun <E : Any> replayState(key: StateKey<E>, handler: EventHandler<E>) {
        val value = currentState(key) ?: return
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
     * The registrations against [id], or null if nothing has ever been registered against it.
     */
    private fun registrationsFor(id: Int): Registrations? {
        val current = registrations
        return if (id < current.length()) current.get(id) else null
    }

    /**
     * Adds [handler] to those registered against [key] with [order], replacing the registrations rather than changing them so that a
     * dispatch already holding them is undisturbed, and reports whether it was not already registered.
     */
    private fun addTo(key: BusKey, handler: EventHandler<*>, order: Int): Boolean {
        synchronized(lock) {
            var current = registrations
            if (key.id >= current.length()) {
                current = grownToHold(current, key.id)
                registrations = current
            }

            val existing = current.get(key.id) ?: Registrations.EMPTY
            val updated = existing.add(handler, order)
            if (updated === existing) {
                return false
            }
            current.set(key.id, updated)
            return true
        }
    }

    /**
     * Removes [handler] from those registered against [key], replacing the registrations rather than changing them so that a
     * dispatch already holding them is undisturbed, and reports whether it was registered.
     */
    private fun removeFrom(key: BusKey, handler: EventHandler<*>): Boolean {
        synchronized(lock) {
            val existing = registrationsFor(key.id) ?: return false
            val updated = existing.remove(handler)
            if (updated === existing) {
                return false
            }
            registrations.set(key.id, updated)
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

    companion object {
        /**
         * The order a handler is registered with when none is given.
         */
        const val DEFAULT_ORDER: Int = 0
    }
}
