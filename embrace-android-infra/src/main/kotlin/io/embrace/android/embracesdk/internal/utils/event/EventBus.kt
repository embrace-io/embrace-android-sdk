package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * A typed event bus where a handler for a type also receives events of any subtype, including subtypes reached through interfaces.
 * Events are emitted on the calling thread, concrete types are handled before their supertypes, and `Any` receives nothing. A
 * [StickyEvent] is retained and replayed to handlers registered after it.
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

    private val dispatch = ConcurrentHashMap<Class<*>, DispatchHandlerSet>()

    private val stickyEvents = StickyEventStore(eventTypeRegistry)

    /**
     * Handlers already reported as having thrown, so that each is reported once. Identity keyed, as registration is.
     */
    private val loggedFailures = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap<EventHandler<*>, Boolean>()))

    /**
     * Registers [handler] against [type] and every subtype of it, replaying any applicable [StickyEvent] before returning. Registering
     * a handler already registered against [type] does nothing.
     */
    fun <E : Any> addHandler(type: Class<E>, handler: EventHandler<E>) {
        if (mutate(handlersFor(type)) { it.add(handler) }) {
            replayStickyEvent(type, handler)
        }
    }

    inline fun <reified E : Any> addHandler(handler: EventHandler<E>) {
        addHandler(E::class.java, handler)
    }

    /**
     * Unregisters [handler] from [type], forgetting any failure reported for it so that registering it again reports again.
     */
    fun <E : Any> removeHandler(type: Class<E>, handler: EventHandler<E>) {
        if (mutate(registered[type]) { it.remove(handler) }) {
            loggedFailures.remove(handler)
        }
    }

    /**
     * Emits [event] to every handler registered against its type or any of its supertypes, on the calling thread.
     */
    fun <E : Any> emit(event: E) {
        // retained before delivery, so a handler registering concurrently sees the event twice rather than not at all
        if (event is StickyEvent) {
            stickyEvents.retain(event)
        }

        deliver(event)
    }

    /**
     * Replays the retained [StickyEvent]s applicable to [type] to a newly registered [handler].
     */
    private fun <E : Any> replayStickyEvent(type: Class<E>, handler: EventHandler<E>) {
        // not gated on [type] being a StickyEvent: a handler on an unmarked supertype receives these on dispatch, so also here
        for (event in stickyEvents.applicableTo(type)) {
            try {
                handler.onEvent(type.cast(event))
            } catch (failure: Exception) {
                reportFailure(handler, failure)
            }
        }
    }

    private fun <E : Any> deliver(event: E) {
        val type = event.javaClass
        val handlers = resolveDispatchHandlers(type)
        handlers.deliver(event)
    }

    private fun reportFailure(handler: EventHandler<*>, failure: Exception) {
        if (loggedFailures.add(handler)) {
            internalErrorHandler.trackInternalError(InternalErrorType.EventBusHandlerFail, failure)
        }
    }

    private fun handlersFor(type: Class<*>): HandlerSet {
        registered[type]?.let { return it }

        val created = HandlerSet()
        return registered.putIfAbsent(type, created) ?: created
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
    private fun resolveDispatchHandlers(type: Class<*>): DispatchHandlerSet {
        // another thread may have resolved this type already
        dispatch[type]?.let { return it }

        val collected = mutableListOf<EventHandler<*>>()
        for (candidate in eventTypeRegistry.hierarchyOf(type)) {
            registered[candidate]?.let { collected.addAll(it.handlers) }
        }

        val resolved = DispatchHandlerSet(collected.toTypedArray())
        return dispatch.putIfAbsent(type, resolved) ?: resolved
    }

    /**
     * A copy-on-write set of the handlers registered directly against one type.
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

    /**
     * The handlers resolved over one event class's whole hierarchy, in the order they are to be invoked. Fixed at construction, as a
     * registration discards it rather than updating it.
     */
    private inner class DispatchHandlerSet(private val handlers: Array<EventHandler<*>>) {
        fun <E : Any> deliver(event: E) {
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
    }
}

/**
 * The most recently emitted [StickyEvent] for each state family, keyed on the family so that a newer value supersedes the older.
 *
 * Nothing is ever evicted, so a [StickyEvent] must not reference anything shorter lived than the process - an `Activity` or `View`
 * reachable from a retained event leaks for the rest of it.
 */
private class StickyEventStore(private val eventTypeRegistry: EventTypeRegistry) {

    private val retained = ConcurrentHashMap<Class<*>, StickyEvent>()

    fun retain(event: StickyEvent) {
        retained[eventTypeRegistry.familyKeyOf(event.javaClass)] = event
    }

    /**
     * The retained events a handler registered against [type] is to receive, tested against each event's own class rather than the
     * family it is keyed under.
     */
    fun applicableTo(type: Class<*>): List<StickyEvent> {
        if (retained.isEmpty()) {
            return emptyList()
        }

        return retained.values.filter { eventTypeRegistry.isSubtype(it.javaClass, of = type) }
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

    private val familyKeys = ConcurrentHashMap<Class<*>, Class<*>>()

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

    /**
     * Whether [type] is [of] or a subtype of it. `Any` is not part of any hierarchy here, so it is never [of].
     */
    fun isSubtype(type: Class<*>, of: Class<*>): Boolean = of in hierarchyOf(type)

    /**
     * The type a [StickyEvent] class is retained against: the most senior type in its hierarchy declaring [StickyEvent] directly, or
     * the class itself where two unrelated types declare it and neither is senior.
     */
    fun familyKeyOf(type: Class<*>): Class<*> {
        familyKeys[type]?.let { return it }

        val resolved = resolveFamilyKey(type)
        return familyKeys.putIfAbsent(type, resolved) ?: resolved
    }

    /**
     * Picks the candidate that is a supertype of all the others; a redundant redeclaration means position cannot be relied on.
     */
    private fun resolveFamilyKey(type: Class<*>): Class<*> {
        val candidates = hierarchyOf(type).filter { StickyEvent::class.java in it.interfaces }

        return candidates.firstOrNull { candidate ->
            candidates.all { it === candidate || isSubtype(it, of = candidate) }
        } ?: type
    }
}
