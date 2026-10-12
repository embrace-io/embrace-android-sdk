package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

internal class EventBusTest {

    private sealed interface AppEvent

    private class ForegroundEvent : AppEvent

    private class BackgroundEvent : AppEvent

    private class ScreenState(val name: String)

    // a state whose values are alternatives of one another
    private sealed interface ConnectionState

    private class Connected(val ssid: String) : ConnectionState

    private object Disconnected : ConnectionState

    private val logger = FakeInternalLogger(throwOnInternalError = false)
    private val bus = EventBus(logger)

    private val appKey = EventKey<AppEvent>()

    private val otherAppKey = EventKey<AppEvent>()

    private val screenEventKey = EventKey<ScreenState>()

    private val screenKey = StateKey<ScreenState>()

    private val otherScreenKey = StateKey<ScreenState>()

    private val connectionKey = StateKey<ConnectionState>()

    /**
     * Creates keys until one has an id beyond any capacity an array could have been sized to while holding [key].
     */
    private fun <K : BusKey> keyBeyondCapacityFor(key: BusKey, create: () -> K): K =
        generateSequence(create).first { it.id >= INITIAL_CAPACITY && it.id > key.id * 2 }

    @Test
    fun `handler receives events emitted against its key`() {
        val received = mutableListOf<AppEvent>()
        bus.addHandler(appKey) { received.add(it) }

        val event = ForegroundEvent()
        bus.emit(appKey, event)

        assertEquals(listOf<AppEvent>(event), received)
    }

    @Test
    fun `handler receives every subtype emitted against its key`() {
        val received = mutableListOf<AppEvent>()
        bus.addHandler(appKey) { received.add(it) }

        val foreground = ForegroundEvent()
        val background = BackgroundEvent()
        bus.emit(appKey, foreground)
        bus.emit(appKey, background)

        assertEquals(listOf(foreground, background), received)
    }

    @Test
    fun `handlers are invoked in registration order`() {
        val order = mutableListOf<String>()
        bus.addHandler(appKey) { order.add("first") }
        bus.addHandler(appKey) { order.add("second") }
        bus.addHandler(appKey) { order.add("third") }

        bus.emit(appKey, ForegroundEvent())

        assertEquals(listOf("first", "second", "third"), order)
    }

    @Test
    fun `handler does not receive events emitted against another key of the same type`() {
        val received = AtomicInteger()
        bus.addHandler(appKey) { received.incrementAndGet() }

        bus.emit(otherAppKey, ForegroundEvent())

        assertEquals(0, received.get())
    }

    @Test
    fun `handlers survive the table growing to hold later keys`() {
        val received = AtomicInteger()
        bus.addHandler(appKey) { received.incrementAndGet() }
        bus.addHandler(keyBeyondCapacityFor(appKey) { EventKey<AppEvent>() }) { }

        bus.emit(appKey, ForegroundEvent())

        assertEquals("handler lost when the table grew", 1, received.get())
    }

    @Test
    fun `emitting against a key with no handlers does nothing`() {
        bus.addHandler(otherAppKey) { }

        bus.emit(appKey, ForegroundEvent())
    }

    @Test
    fun `adding the same handler twice only registers it once`() {
        val received = AtomicInteger()
        val handler = EventHandler<AppEvent> { received.incrementAndGet() }

        bus.addHandler(appKey, handler)
        bus.addHandler(appKey, handler)
        bus.emit(appKey, ForegroundEvent())

        assertEquals(1, received.get())
    }

    @Test
    fun `removed handler stops receiving events`() {
        val received = AtomicInteger()
        val handler = EventHandler<AppEvent> { received.incrementAndGet() }
        bus.addHandler(appKey, handler)

        bus.emit(appKey, ForegroundEvent())
        bus.removeHandler(appKey, handler)
        bus.emit(appKey, ForegroundEvent())

        assertEquals("handler invoked after removal", 1, received.get())
    }

    @Test
    fun `removing an unregistered handler is a no-op`() {
        val received = AtomicInteger()
        bus.addHandler(appKey) { received.incrementAndGet() }

        bus.removeHandler(appKey, EventHandler { })
        bus.removeHandler(otherAppKey, EventHandler { })
        bus.emit(appKey, ForegroundEvent())

        assertEquals(1, received.get())
    }

    @Test
    fun `handler added after a key has been emitted against receives subsequent events`() {
        val first = AtomicInteger()
        val second = AtomicInteger()
        bus.addHandler(appKey) { first.incrementAndGet() }

        bus.emit(appKey, ForegroundEvent())
        bus.addHandler(appKey) { second.incrementAndGet() }
        bus.emit(appKey, ForegroundEvent())

        assertEquals(2, first.get())
        assertEquals("handler added after the first dispatch was not picked up", 1, second.get())
    }

    @Test
    fun `a handler that throws during dispatch does not stop the handlers after it`() {
        val received = AtomicInteger()
        bus.addHandler(appKey) { error("boom") }
        bus.addHandler(appKey) { received.incrementAndGet() }

        bus.emit(appKey, ForegroundEvent())

        assertEquals("the handler after the throwing one was not invoked", 1, received.get())
        assertEquals(listOf(InternalErrorType.EventBusHandlerFail.toString()), logger.internalErrorMessages.map { it.msg })
    }

    @Test
    fun `a handler that throws on every event is reported once`() {
        bus.addHandler(appKey) { error("boom") }

        repeat(3) { bus.emit(appKey, ForegroundEvent()) }

        assertEquals(1, logger.internalErrorMessages.size)
    }

    @Test
    fun `a handler that threw is reported again once removed and re-registered`() {
        val handler = EventHandler<AppEvent> { error("boom") }

        bus.addHandler(appKey, handler)
        bus.emit(appKey, ForegroundEvent())
        bus.removeHandler(appKey, handler)
        bus.addHandler(appKey, handler)
        bus.emit(appKey, ForegroundEvent())

        assertEquals(2, logger.internalErrorMessages.size)
    }

    @Test
    fun `handler registered during an emit does not disturb the in-flight dispatch`() {
        val added = AtomicInteger()
        bus.addHandler(appKey) {
            bus.addHandler(appKey) { added.incrementAndGet() }
        }

        bus.emit(appKey, ForegroundEvent())
        assertEquals("handler registered mid-emit was invoked for the in-flight event", 0, added.get())

        bus.emit(appKey, ForegroundEvent())
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
    fun `a retained value survives the values growing to hold later keys`() {
        bus.emitState(screenKey, ScreenState("home"))
        bus.emitState(keyBeyondCapacityFor(screenKey) { StateKey<ScreenState>() }, ScreenState("details"))

        val received = mutableListOf<String>()
        bus.addStateHandler(screenKey) { received.add(it.name) }

        assertEquals(listOf("home"), received)
    }

    @Test
    fun `a handler that throws during state replay is reported once, like a live dispatch failure`() {
        bus.emitState(screenKey, ScreenState("home"))

        bus.addStateHandler(screenKey) { error("boom") }

        assertEquals(listOf(InternalErrorType.EventBusHandlerFail.toString()), logger.internalErrorMessages.map { it.msg })
    }

    @Test
    fun `a value emitted against a state key does not reach a handler registered against an event key`() {
        val received = AtomicInteger()
        bus.addHandler(screenEventKey) { received.incrementAndGet() }

        bus.emitState(screenKey, ScreenState("home"))

        assertEquals("a state value leaked into event dispatch", 0, received.get())
    }

    @Test
    fun `an event emitted against an event key does not reach a handler registered against a state key`() {
        val received = AtomicInteger()
        bus.addStateHandler(screenKey) { received.incrementAndGet() }

        bus.emit(screenEventKey, ScreenState("home"))

        assertEquals(0, received.get())
    }

    @Test
    fun `an event is not replayed to a handler registered afterwards`() {
        bus.emit(appKey, ForegroundEvent())

        val received = AtomicInteger()
        bus.addHandler(appKey) { received.incrementAndGet() }

        assertEquals(0, received.get())
    }
}
