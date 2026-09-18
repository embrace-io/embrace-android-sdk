package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * A typed event bus where a handler for a type also receives events of any subtype, including subtypes reached through interfaces.
 * Events are emitted on the calling thread, concrete types are handled before their supertypes, and `Any` receives nothing.
 *
 * State travels separately, against a [StateKey]: a value emitted against one is retained and replayed to handlers registered
 * against that key afterwards, and reaches no handler registered against a type.
 *
 * Handlers are expected to be registered during initialisation, before any thread that emits exists; registering one while another
 * thread is emitting risks that handler silently receiving nothing for an event type.
 */
class EventBus(private val internalErrorHandler: InternalErrorHandler) {

    private val eventTypeRegistry = EventTypeRegistry()

    /**
     * Handlers registered directly against a given type.
     */
    private val registered = ConcurrentHashMap<Class<*>, HandlerSet>()

    /**
     * The handlers resolved over one event class's whole hierarchy, in the order they are to be invoked; a registration discards
     * these rather than updating them.
     */
    private val dispatch = ConcurrentHashMap<Class<*>, Array<EventHandler<*>>>()

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
     * Registers [handler] against [type] and every subtype of it. Registering a handler already registered against [type] does
     * nothing.
     */
    fun <E : Any> addHandler(type: Class<E>, handler: EventHandler<E>) {
        mutate(handlersFor(registered, type)) { it.add(handler) }
    }

    inline fun <reified E : Any> addHandler(handler: EventHandler<E>) {
        addHandler(E::class.java, handler)
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
     * Unregisters [handler] from [type], forgetting any failure reported for it so that registering it again, reports again.
     */
    fun <E : Any> removeHandler(type: Class<E>, handler: EventHandler<E>) {
        if (mutate(registered[type]) { it.remove(handler) }) {
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
     * Emits [event] to every handler registered against its type or any of its supertypes, on the calling thread, retaining nothing.
     */
    fun <E : Any> emit(event: E) {
        deliverTo(resolveDispatchHandlers(event.javaClass), event)
    }

    /**
     * Emits [value] as the current value of the state [key] names, retaining it for replay and delivering it to the handlers
     * registered against [key].
     *
     * Emitting against a [key] supersedes whatever it held, so alternative values of one state share a key. The value reaches no
     * handler registered against a type, which is what [emit] is for.
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
            @Suppress("UNCHECKED_CAST")
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
     * Applies [action] to [handlerSet], discarding the resolved handlers if it changed anything, and reports whether it did.
     */
    private inline fun mutate(handlerSet: HandlerSet?, action: (HandlerSet) -> Boolean): Boolean {
        if (handlerSet == null || !action(handlerSet)) {
            return false
        }

        dispatch.clear()
        return true
    }

    /**
     * Flattens the handlers registered anywhere in [type]'s hierarchy into the order they are to be invoked in.
     */
    private fun resolveDispatchHandlers(type: Class<*>): Array<EventHandler<*>> {
        // another thread may have resolved this type already
        dispatch[type]?.let { return it }

        val collected = mutableListOf<EventHandler<*>>()
        for (candidate in eventTypeRegistry.hierarchyOf(type)) {
            registered[candidate]?.let { collected.addAll(it.handlers) }
        }

        val resolved = collected.toTypedArray()
        return dispatch.putIfAbsent(type, resolved) ?: resolved
    }

    /**
     * A copy-on-write set of the handlers registered directly against one type or [StateKey].
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

/**
 * Answers questions about an event type's place in its own type hierarchy, memoising every answer.
 *
 * A type hierarchy is fixed for the life of the process, so nothing here is ever invalidated, and answering for one type also caches
 * the answer for every supertype reached on the way.
 */
private class EventTypeRegistry {

    private val hierarchies = ConcurrentHashMap<Class<*>, Array<Class<*>>>()

    /**
     * [type]'s hierarchy in the order handlers are to be invoked: the type, then its interfaces depth first in declaration order, then
     * its superclass. A type reached by more than one path appears only at the first, and `Any` is never included.
     */
    fun hierarchyOf(type: Class<*>): Array<Class<*>> {
        if (type == Any::class.java) {
            return emptyArray()
        }
        hierarchies[type]?.let { return it }

        val hierarchy = linkedSetOf(type)
        type.interfaces.forEach { hierarchy.addAll(hierarchyOf(it)) }
        type.superclass?.let { hierarchy.addAll(hierarchyOf(it)) }

        val resolved = hierarchy.toTypedArray()
        return hierarchies.putIfAbsent(type, resolved) ?: resolved
    }
}
