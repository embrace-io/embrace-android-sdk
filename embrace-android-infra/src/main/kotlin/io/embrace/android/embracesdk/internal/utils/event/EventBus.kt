package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple typed `EventBus` that can emit events of any type (allowing the direct use of platform events where appropriate). A handler
 * for a type receives events of any subtype as well, including subtypes reached through interfaces, allowing `sealed class` or
 * `sealed interface` hierarchies of events (or similar) if desired. Events are always emitted on the calling thread, and the bus is
 * thread safe.
 *
 * Handlers registered against a concrete event type are invoked before those registered against its supertypes. `Any` is not part of any
 * type hierarchy walked by this bus, so a handler registered against `Any` never receives events.
 *
 * Handlers are expected to be registered once during initialisation and left in place; [removeHandler] exists for edge cases and tests.
 * Registration is therefore the cold path, and all type hierarchy resolution is done there rather than during [emit].
 */
class EventBus(private val internalErrorHandler: InternalErrorHandler) {

    /**
     * Handlers registered directly against a given type.
     */
    private val registered = ConcurrentHashMap<Class<*>, Handlers>()

    /**
     * Handlers resolved over the full type hierarchy of a concrete event class, populated lazily by [emit] and cleared by [mutate]. Written
     * only while holding this bus's monitor, so an entry cannot be published from a resolution that a registration has already invalidated;
     * [emit] reads it without locking.
     */
    private val dispatch = ConcurrentHashMap<Class<*>, Handlers>()

    private val loggedFailures = Collections.synchronizedSet<EventHandler<*>>(HashSet())

    fun <E : Any> addHandler(type: Class<E>, handler: EventHandler<E>) {
        mutate(handlersFor(type)) { it.add(handler) }
    }

    inline fun <reified E : Any> addHandler(handler: EventHandler<E>) {
        addHandler(E::class.java, handler)
    }

    fun <E : Any> removeHandler(type: Class<E>, handler: EventHandler<E>) {
        mutate(registered[type]) { it.remove(handler) }
    }

    fun <E : Any> emit(event: E) {
        val type = event.javaClass
        val handlers = dispatch[type]?.values ?: resolve(type)

        for (i in handlers.indices) {
            @Suppress("UNCHECKED_CAST")
            val handler = handlers[i] as EventHandler<E>
            try {
                handler.onEvent(event)
            } catch (failure: Exception) {
                if (loggedFailures.add(handler)) {
                    internalErrorHandler.trackInternalError(InternalErrorType.EventBusHandlerFail, failure)
                }
            }
        }
    }

    private fun handlersFor(type: Class<*>): Handlers {
        registered[type]?.let { return it }

        val created = Handlers()
        return registered.putIfAbsent(type, created) ?: created
    }

    /**
     * Applies a registration change and invalidates the resolved handlers. Every mutation must go through here so that [dispatch] cannot be
     * left holding a stale resolution. Both this and [resolve] hold the bus's monitor, which is what stops a resolution that read the
     * pre-change handlers from being published after the invalidation. Registration is cold, so the lock is uncontended; `mutate` is inline
     * and `clear` allocates nothing, as dozens of handlers are registered during startup.
     */
    private inline fun mutate(handlers: Handlers?, action: (Handlers) -> Unit) {
        synchronized(this) {
            handlers?.let(action)
            dispatch.clear()
        }
    }

    /**
     * Resolves and caches the handlers for a concrete event class, on first emit of that class and again after any registration change.
     */
    @Synchronized
    private fun resolve(type: Class<*>): Array<EventHandler<*>> {
        // another thread may have resolved this type while this one waited for the monitor
        dispatch[type]?.let { return it.values }

        val collected = mutableListOf<EventHandler<*>>()
        collectHandlers(type, collected, mutableSetOf())

        val resolved = Handlers(collected.toTypedArray())
        dispatch[type] = resolved
        return resolved.values
    }

    /**
     * Walks [type], its interfaces, and its superclass, collecting the handlers registered against each. The visited set both terminates
     * the walk and stops a type reachable by more than one path - as interfaces commonly are - from contributing its handlers twice.
     */
    private fun collectHandlers(
        type: Class<*>,
        collected: MutableList<EventHandler<*>>,
        visited: MutableSet<Class<*>>,
    ) {
        if (type == Any::class.java || !visited.add(type)) {
            return
        }

        registered[type]?.let { collected.addAll(it.values) }
        type.interfaces.forEach { collectHandlers(it, collected, visited) }
        type.superclass?.let { collectHandlers(it, collected, visited) }
    }

    /**
     * A copy-on-write set of handlers. Used both for the handlers registered against a single type and for the handlers resolved over a
     * concrete event class's type hierarchy, so there is only one representation to keep in step. Holding them as an array lets [emit]
     * iterate without allocating, and [values] is volatile so that it can do so without locking.
     */
    private class Handlers(initial: Array<EventHandler<*>> = emptyArray()) {

        @Volatile
        var values: Array<EventHandler<*>> = initial
            private set

        @Synchronized
        fun add(handler: EventHandler<*>) {
            if (values.none { it === handler }) {
                values += handler
            }
        }

        @Synchronized
        fun remove(handler: EventHandler<*>) {
            values = values.filterNot { it === handler }.toTypedArray()
        }
    }
}
