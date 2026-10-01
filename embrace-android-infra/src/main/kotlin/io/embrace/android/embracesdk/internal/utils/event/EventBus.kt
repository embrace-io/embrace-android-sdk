package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * A keyed event bus where an event emitted against an [EventKey] reaches only the handlers registered against that key, on the
 * calling thread, in the order they were registered.
 *
 * State travels separately, against a [StateKey]: a value emitted against one is retained and replayed to handlers registered
 * against that key afterwards.
 */
class EventBus(private val internalErrorHandler: InternalErrorHandler) {

    /**
     * Handlers registered against a given [EventKey].
     */
    private val eventHandlers = ConcurrentHashMap<EventKey<*>, HandlerSet>()

    /**
     * Handlers registered against a given [StateKey].
     */
    private val stateHandlers = ConcurrentHashMap<StateKey<*>, HandlerSet>()

    /**
     * The most recently emitted value for each [StateKey], so that a newer value supersedes the older.
     *
     * Nothing is ever evicted, so a retained value must not reference anything shorter lived than the process - an `Activity` or
     * `View` reachable from one leaks for the rest of it.
     */
    private val states = ConcurrentHashMap<StateKey<*>, Any>()

    /**
     * Handlers already reported as having thrown, so that each is reported once. Identity keyed, as registration is.
     */
    private val loggedFailures = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap<EventHandler<*>, Boolean>()))

    /**
     * Registers [handler] against [key]. Registering a handler already registered against [key] does nothing.
     */
    fun <E : Any> addHandler(key: EventKey<E>, handler: EventHandler<E>) {
        handlersFor(eventHandlers, key).add(handler)
    }

    /**
     * Registers [handler] against [key], replaying the value retained against it before returning. Registering a handler already
     * registered against [key] does nothing.
     */
    fun <E : Any> addStateHandler(key: StateKey<E>, handler: EventHandler<E>) {
        if (handlersFor(stateHandlers, key).add(handler)) {
            replayState(key, handler)
        }
    }

    /**
     * Unregisters [handler] from [key], forgetting any failure reported for it so that registering it again, reports again.
     */
    fun <E : Any> removeHandler(key: EventKey<E>, handler: EventHandler<E>) {
        if (eventHandlers[key]?.remove(handler) == true) {
            loggedFailures.remove(handler)
        }
    }

    /**
     * Unregisters [handler] from [key], forgetting any failure reported for it so that registering it again, reports again.
     */
    fun <E : Any> removeStateHandler(key: StateKey<E>, handler: EventHandler<E>) {
        if (stateHandlers[key]?.remove(handler) == true) {
            loggedFailures.remove(handler)
        }
    }

    /**
     * Emits [event] to every handler registered against [key], on the calling thread, retaining nothing.
     */
    fun <E : Any> emit(key: EventKey<E>, event: E) {
        eventHandlers[key]?.let { deliverTo(it.handlers, event) }
    }

    /**
     * Emits [value] as the current value of the state [key] names, retaining it for replay and delivering it to the handlers
     * registered against [key].
     *
     * Emitting against a [key] supersedes whatever it held, so alternative values of one state share a key.
     */
    fun <E : Any> emitState(key: StateKey<E>, value: E) {
        // retained before delivery, so a handler registering concurrently sees the value twice rather than not at all
        states[key] = value
        stateHandlers[key]?.let { deliverTo(it.handlers, value) }
    }

    /**
     * Replays the value retained against [key], if there is one, to a newly registered [handler].
     */
    private fun <E : Any> replayState(key: StateKey<E>, handler: EventHandler<E>) {
        val retained = states[key] ?: return

        @Suppress("UNCHECKED_CAST") // emitState is the only writer, and it pairs a key with a value of that key's own type
        val value = retained as E
        try {
            handler.onEvent(value)
        } catch (failure: Exception) {
            reportFailure(handler, failure)
        }
    }

    private fun <E : Any> deliverTo(handlers: Array<EventHandler<*>>, event: E) {
        for (handler in handlers) {
            @Suppress("UNCHECKED_CAST") // addHandler and addStateHandler pair a key with handlers of that key's own type
            val typed = handler as EventHandler<E>
            try {
                typed.onEvent(event)
            } catch (failure: Exception) {
                reportFailure(handler, failure)
            }
        }
    }

    private fun reportFailure(handler: EventHandler<*>, failure: Exception) {
        if (loggedFailures.add(handler)) {
            internalErrorHandler.trackInternalError(InternalErrorType.EventBusHandlerFail, failure)
        }
    }

    private fun <K : Any> handlersFor(registry: ConcurrentHashMap<K, HandlerSet>, key: K): HandlerSet {
        registry[key]?.let { return it }

        val created = HandlerSet()
        return registry.putIfAbsent(key, created) ?: created
    }

    /**
     * A copy-on-write set of the handlers registered against one [EventKey] or [StateKey].
     */
    private class HandlerSet {
        @Volatile
        var handlers: Array<EventHandler<*>> = emptyArray()
            private set

        @Synchronized
        fun add(handler: EventHandler<*>): Boolean {
            if (handlers.none { it === handler }) {
                handlers += handler
                return true
            }
            return false
        }

        @Synchronized
        fun remove(handler: EventHandler<*>): Boolean {
            val existingHandlers = handlers
            if (existingHandlers.none { it === handler }) {
                return false
            }
            handlers = existingHandlers.filterNot { it === handler }.toTypedArray()
            return true
        }
    }
}
