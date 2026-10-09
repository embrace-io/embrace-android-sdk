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
 */
class EventBus(private val internalErrorHandler: InternalErrorHandler) {

    /**
     * Guards every change to [registrations].
     */
    private val lock = Any()

    /**
     * The handlers registered against each key, indexed by [BusKey.id], written and replaced only under [lock].
     *
     * This `AtomicReferenceArray` is heterogeneous and each slot's array is created with the element type of what it holds
     * (`Array<EventHandler>`), so that it can be cast whole by [registeredFor].
     */
    @Volatile
    private var registrations = AtomicReferenceArray<Array<*>?>(INITIAL_CAPACITY)

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
     * Unregisters [handler] from [key], forgetting any failure reported for it so that registering it again, reports again.
     */
    fun <E : Any> removeHandler(key: EventKey<E>, handler: EventHandler<E>) {
        if (removeFrom<EventHandler<*>>(key, handler)) {
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

    private fun <E : Any> deliverTo(registered: Array<EventHandler<*>>, event: E) {
        for (handler in registered) {
            @Suppress("UNCHECKED_CAST") // addHandler pairs a key with handlers of that key's own type
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
