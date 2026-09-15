package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

internal class EventBusTest {

    private sealed interface AppEvent

    private interface Loggable

    private open class BaseEvent : AppEvent

    private class ForegroundEvent : BaseEvent(), Loggable

    private class UnrelatedEvent

    // reached by two paths
    private interface Root

    private interface Left : Root

    private interface Right : Root

    private class DiamondEvent : Left, Right

    private class RightEvent : Right

    private class ScreenState(val name: String)

    // a state whose values are alternatives of one another
    private sealed interface ConnectionState

    private class Connected(val ssid: String) : ConnectionState

    private object Disconnected : ConnectionState

    private val logger = FakeInternalLogger(throwOnInternalError = false)
    private val bus = EventBus(logger)

    private val screenKey = StateKey<ScreenState>()

    private val otherScreenKey = StateKey<ScreenState>()

    private val connectionKey = StateKey<ConnectionState>()

    @Test
    fun `handler receives events of its own type`() {
        val received = mutableListOf<ForegroundEvent>()
        bus.addHandler<ForegroundEvent> { received.add(it) }

        val event = ForegroundEvent()
        bus.emit(event)

        assertEquals(listOf(event), received)
    }

    @Test
    fun `handler receives events of a subtype`() {
        val received = mutableListOf<BaseEvent>()
        bus.addHandler<BaseEvent> { received.add(it) }

        val event = ForegroundEvent()
        bus.emit(event)

        assertEquals(listOf<BaseEvent>(event), received)
    }

    @Test
    fun `handler registered against a sealed interface receives events of an implementing type`() {
        val received = mutableListOf<AppEvent>()
        bus.addHandler<AppEvent> { received.add(it) }

        val event = ForegroundEvent()
        bus.emit(event)

        assertEquals(listOf<AppEvent>(event), received)
    }

    @Test
    fun `handler registered against an interface implemented directly receives events`() {
        val received = mutableListOf<Loggable>()
        bus.addHandler<Loggable> { received.add(it) }

        val event = ForegroundEvent()
        bus.emit(event)

        assertEquals(listOf<Loggable>(event), received)
    }

    @Test
    fun `handlers are invoked most specific type first`() {
        val order = mutableListOf<String>()
        bus.addHandler<AppEvent> { order.add("interface") }
        bus.addHandler<BaseEvent> { order.add("superclass") }
        bus.addHandler<ForegroundEvent> { order.add("concrete") }

        bus.emit(ForegroundEvent())

        assertEquals(listOf("concrete", "superclass", "interface"), order)
    }

    @Test
    fun `handler registered against a type reachable by multiple paths is invoked once`() {
        // ForegroundEvent implements Loggable directly, and reaches AppEvent via BaseEvent.
        val appEventCount = AtomicInteger()
        bus.addHandler<AppEvent> { appEventCount.incrementAndGet() }
        // Register the same type again via BaseEvent's hierarchy to ensure the walk dedupes types, not just handlers.
        bus.addHandler<BaseEvent> { }

        bus.emit(ForegroundEvent())

        assertEquals(1, appEventCount.get())
    }

    @Test
    fun `interfaces declared on a type are invoked before its superclass`() {
        val order = mutableListOf<String>()
        bus.addHandler<AppEvent> { order.add("AppEvent") }
        bus.addHandler<BaseEvent> { order.add("BaseEvent") }
        bus.addHandler<Loggable> { order.add("Loggable") }
        bus.addHandler<ForegroundEvent> { order.add("ForegroundEvent") }

        bus.emit(ForegroundEvent())

        assertEquals(listOf("ForegroundEvent", "Loggable", "BaseEvent", "AppEvent"), order)
    }

    @Test
    fun `a type reachable by two paths is invoked at its first position, not its last`() {
        val order = mutableListOf<String>()
        bus.addHandler<Root> { order.add("Root") }
        bus.addHandler<Left> { order.add("Left") }
        bus.addHandler<Right> { order.add("Right") }

        bus.emit(DiamondEvent())

        assertEquals(listOf("Left", "Root", "Right"), order)
    }

    @Test
    fun `resolving a subtype leaves a supertype's own hierarchy complete`() {
        val received = mutableListOf<String>()
        bus.addHandler<Root> { received.add("Root") }

        // DiamondEvent reaches Root through Left, so Right must not be left without it
        bus.emit(DiamondEvent())
        received.clear()
        bus.emit(RightEvent())

        assertEquals("Right lost the Root reached by a subtype's walk", listOf("Root"), received)
    }

    @Test
    fun `handler registered against Any never receives events`() {
        val received = AtomicInteger()
        bus.addHandler<Any> { received.incrementAndGet() }

        bus.emit(ForegroundEvent())
        bus.emit(UnrelatedEvent())

        assertEquals(0, received.get())
    }

    @Test
    fun `handler does not receive events of unrelated types`() {
        val received = AtomicInteger()
        bus.addHandler<ForegroundEvent> { received.incrementAndGet() }

        bus.emit(UnrelatedEvent())

        assertEquals(0, received.get())
    }

    @Test
    fun `emitting an event with no handlers does nothing`() {
        bus.addHandler<UnrelatedEvent> { }

        bus.emit(ForegroundEvent())
    }

    @Test
    fun `adding the same handler twice only registers it once`() {
        val received = AtomicInteger()
        val handler = EventHandler<ForegroundEvent> { received.incrementAndGet() }

        bus.addHandler(ForegroundEvent::class.java, handler)
        bus.addHandler(ForegroundEvent::class.java, handler)
        bus.emit(ForegroundEvent())

        assertEquals(1, received.get())
    }

    @Test
    fun `removed handler stops receiving events`() {
        val received = AtomicInteger()
        val handler = EventHandler<BaseEvent> { received.incrementAndGet() }
        bus.addHandler(BaseEvent::class.java, handler)

        bus.emit(ForegroundEvent())
        bus.removeHandler(BaseEvent::class.java, handler)
        bus.emit(ForegroundEvent())

        assertEquals("handler invoked after removal", 1, received.get())
    }

    @Test
    fun `removing an unregistered handler is a no-op`() {
        val received = AtomicInteger()
        bus.addHandler<ForegroundEvent> { received.incrementAndGet() }

        bus.removeHandler(ForegroundEvent::class.java, EventHandler { })
        bus.removeHandler(UnrelatedEvent::class.java, EventHandler { })
        bus.emit(ForegroundEvent())

        assertEquals(1, received.get())
    }

    @Test
    fun `handler added after an event type has been dispatched receives subsequent events`() {
        val first = AtomicInteger()
        val second = AtomicInteger()
        bus.addHandler<ForegroundEvent> { first.incrementAndGet() }

        // Dispatch once so the type hierarchy is resolved and cached, then invalidate it by registering against a supertype.
        bus.emit(ForegroundEvent())
        bus.addHandler<AppEvent> { second.incrementAndGet() }
        bus.emit(ForegroundEvent())

        assertEquals(2, first.get())
        assertEquals("handler added after the first dispatch was not picked up", 1, second.get())
    }

    @Test
    fun `a handler that throws during dispatch does not stop the handlers after it`() {
        val received = AtomicInteger()
        bus.addHandler<ForegroundEvent> { error("boom") }
        bus.addHandler<ForegroundEvent> { received.incrementAndGet() }

        bus.emit(ForegroundEvent())

        assertEquals("the handler after the throwing one was not invoked", 1, received.get())
        assertEquals(listOf(InternalErrorType.EventBusHandlerFail.toString()), logger.internalErrorMessages.map { it.msg })
    }

    @Test
    fun `a handler that throws on every event is reported once`() {
        bus.addHandler<ForegroundEvent> { error("boom") }

        repeat(3) { bus.emit(ForegroundEvent()) }

        assertEquals(1, logger.internalErrorMessages.size)
    }

    @Test
    fun `a handler that threw is reported again once removed and re-registered`() {
        val handler = EventHandler<ForegroundEvent> { error("boom") }

        bus.addHandler(ForegroundEvent::class.java, handler)
        bus.emit(ForegroundEvent())
        bus.removeHandler(ForegroundEvent::class.java, handler)
        bus.addHandler(ForegroundEvent::class.java, handler)
        bus.emit(ForegroundEvent())

        assertEquals(2, logger.internalErrorMessages.size)
    }

    @Test
    fun `handler registered during an emit does not disturb the in-flight dispatch`() {
        val added = AtomicInteger()
        bus.addHandler<ForegroundEvent> {
            bus.addHandler<ForegroundEvent> { added.incrementAndGet() }
        }

        bus.emit(ForegroundEvent())
        assertEquals("handler registered mid-emit was invoked for the in-flight event", 0, added.get())

        bus.emit(ForegroundEvent())
        assertEquals(1, added.get())
    }

    @Test
    fun `a handler registered against a key receives values emitted against it`() {
        val received = mutableListOf<String>()
        bus.addStateHandler(screenKey) { received.add(it.name) }

        bus.emitState(screenKey, ScreenState("home"))

        assertEquals(listOf("home"), received)
    }

    @Test
    fun `a handler registered after a state emit is immediately replayed the most recent value`() {
        bus.emitState(screenKey, ScreenState("home"))

        val received = mutableListOf<String>()
        bus.addStateHandler(screenKey) { received.add(it.name) }

        assertEquals(listOf("home"), received)
    }

    @Test
    fun `a late handler is replayed only the most recent value, not every prior one`() {
        bus.emitState(screenKey, ScreenState("home"))
        bus.emitState(screenKey, ScreenState("details"))

        val received = mutableListOf<String>()
        bus.addStateHandler(screenKey) { received.add(it.name) }

        assertEquals(listOf("details"), received)
    }

    @Test
    fun `a key that has had nothing emitted against it replays nothing`() {
        val received = AtomicInteger()
        bus.addStateHandler(screenKey) { received.incrementAndGet() }

        assertEquals(0, received.get())
    }

    @Test
    fun `registering the same handler twice against a key does not replay twice`() {
        bus.emitState(screenKey, ScreenState("home"))

        val received = mutableListOf<String>()
        val handler = EventHandler<ScreenState> { received.add(it.name) }
        bus.addStateHandler(screenKey, handler)
        bus.addStateHandler(screenKey, handler)

        assertEquals(listOf("home"), received)
    }

    @Test
    fun `a handler removed from a key stops receiving values`() {
        val received = AtomicInteger()
        val handler = EventHandler<ScreenState> { received.incrementAndGet() }
        bus.addStateHandler(screenKey, handler)

        bus.emitState(screenKey, ScreenState("home"))
        bus.removeStateHandler(screenKey, handler)
        bus.emitState(screenKey, ScreenState("details"))

        assertEquals("handler invoked after removal", 1, received.get())
    }

    @Test
    fun `keys of the same value type are independent of one another`() {
        bus.emitState(screenKey, ScreenState("home"))
        bus.emitState(otherScreenKey, ScreenState("details"))

        val received = mutableListOf<String>()
        bus.addStateHandler(screenKey) { received.add(it.name) }

        assertEquals(listOf("home"), received)
    }

    @Test
    fun `alternative values emitted against one key share a single retained slot`() {
        bus.emitState(connectionKey, Connected("home-wifi"))
        bus.emitState(connectionKey, Disconnected)

        val received = mutableListOf<String>()
        bus.addStateHandler(connectionKey) { received.add(it.javaClass.simpleName) }

        assertEquals(listOf("Disconnected"), received)
    }

    @Test
    fun `a handler that throws during state replay is reported once, like a live dispatch failure`() {
        bus.emitState(screenKey, ScreenState("home"))

        bus.addStateHandler(screenKey) { error("boom") }

        assertEquals(listOf(InternalErrorType.EventBusHandlerFail.toString()), logger.internalErrorMessages.map { it.msg })
    }

    @Test
    fun `a value emitted against a key does not reach a handler registered against its type`() {
        val received = AtomicInteger()
        bus.addHandler<ScreenState> { received.incrementAndGet() }

        bus.emitState(screenKey, ScreenState("home"))

        assertEquals("a state value leaked into type dispatch", 0, received.get())
    }

    @Test
    fun `an event emitted by type does not reach a handler registered against a key`() {
        val received = AtomicInteger()
        bus.addStateHandler(screenKey) { received.incrementAndGet() }

        bus.emit(ScreenState("home"))

        assertEquals(0, received.get())
    }

    @Test
    fun `an event emitted by type is not replayed to a handler registered afterwards`() {
        bus.emit(UnrelatedEvent())

        val received = AtomicInteger()
        bus.addHandler<UnrelatedEvent> { received.incrementAndGet() }

        assertEquals(0, received.get())
    }
}
