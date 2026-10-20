package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Creates keys until one has an id beyond any capacity an array could have been sized to while holding [key].
 */
internal fun <K : BusKey> keyBeyondCapacityFor(key: BusKey, create: () -> K): K =
    generateSequence(create).first { it.id >= INITIAL_CAPACITY && it.id > key.id * 2 }

internal class EventBusTest {

    private sealed interface AppEvent

    private class ForegroundEvent : AppEvent

    private class BackgroundEvent : AppEvent

    private class ScreenState(val name: String)

    // a state whose values are alternatives of one another
    private sealed interface ConnectionState

    private class Connected(val ssid: String) : ConnectionState

    private object Disconnected : ConnectionState

    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private val bus = EventBus(errorHandler)

    private val appKey = EventKey<AppEvent>()

    private val otherAppKey = EventKey<AppEvent>()

    private val screenEventKey = EventKey<ScreenState>()

    private val screenKey = StateKey<ScreenState>()

    private val otherScreenKey = StateKey<ScreenState>()

    private val connectionKey = StateKey<ConnectionState>()

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
    fun `handlers are invoked in ascending order, whatever order they were registered in`() {
        val delivered = mutableListOf<String>()
        bus.addHandler(appKey, 300) { delivered.add("last") }
        bus.addHandler(appKey, -100) { delivered.add("first") }
        bus.addHandler(appKey) { delivered.add("default") }
        bus.addHandler(appKey, 100) { delivered.add("later") }

        bus.emit(appKey, ForegroundEvent())

        assertEquals(listOf("first", "default", "later", "last"), delivered)
    }

    @Test
    fun `handlers with the same order are invoked in registration order`() {
        val delivered = mutableListOf<String>()
        bus.addHandler(appKey, 100) { delivered.add("first at 100") }
        bus.addHandler(appKey) { delivered.add("default") }
        bus.addHandler(appKey, 100) { delivered.add("second at 100") }

        bus.emit(appKey, ForegroundEvent())

        assertEquals(listOf("default", "first at 100", "second at 100"), delivered)
    }

    @Test
    fun `removing a handler keeps the order of the rest`() {
        val delivered = mutableListOf<String>()
        val removed = EventHandler<AppEvent> { delivered.add("removed") }
        bus.addHandler(appKey, 200) { delivered.add("last") }
        bus.addHandler(appKey, 100, removed)
        bus.addHandler(appKey, 0) { delivered.add("first") }
        bus.removeHandler(appKey, removed)
        bus.addHandler(appKey, 100) { delivered.add("middle") }

        bus.emit(appKey, ForegroundEvent())

        assertEquals(listOf("first", "middle", "last"), delivered)
    }

    @Test
    fun `state handlers are delivered live values in ascending order`() {
        val delivered = mutableListOf<String>()
        bus.addStateHandler(screenKey, order = 100) { delivered.add("later ${it.name}") }
        bus.addStateHandler(screenKey, order = -100) { delivered.add("first ${it.name}") }

        bus.emitState(screenKey, ScreenState("home"))

        assertEquals(listOf("first home", "later home"), delivered)
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
        assertEquals(
            listOf(SdkErrorType.EventBusHandlerFail.toString()),
            errorHandler.sdkErrorMessages.map { it.msg },
        )
    }

    @Test
    fun `a handler that throws an Error during dispatch is reported rather than thrown`() {
        val received = AtomicInteger()
        bus.addHandler(appKey) { throw NoClassDefFoundError("boom") }
        bus.addHandler(appKey) { received.incrementAndGet() }

        bus.emit(appKey, ForegroundEvent())

        assertEquals("the handler after the throwing one was not invoked", 1, received.get())
        assertEquals(
            listOf(SdkErrorType.EventBusHandlerFail.toString()),
            errorHandler.sdkErrorMessages.map { it.msg },
        )
    }

    @Test
    fun `a handler that throws on every event is reported once`() {
        bus.addHandler(appKey) { error("boom") }

        repeat(3) { bus.emit(appKey, ForegroundEvent()) }

        assertEquals(1, errorHandler.sdkErrorMessages.size)
    }

    @Test
    fun `a handler that threw is reported again once removed and re-registered`() {
        val handler = EventHandler<AppEvent> { error("boom") }

        bus.addHandler(appKey, handler)
        bus.emit(appKey, ForegroundEvent())
        bus.removeHandler(appKey, handler)
        bus.addHandler(appKey, handler)
        bus.emit(appKey, ForegroundEvent())

        assertEquals(2, errorHandler.sdkErrorMessages.size)
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
        bus.addStateHandler(screenKey, handler = handler)
        bus.addStateHandler(screenKey, handler = handler)

        assertEquals(listOf("home"), received)
    }

    @Test
    fun `a handler registered without replay is not given the retained value`() {
        bus.emitState(screenKey, ScreenState("home"))

        val received = mutableListOf<String>()
        bus.addStateHandler(screenKey, replay = false) { received.add(it.name) }

        assertEquals(emptyList<String>(), received)
    }

    @Test
    fun `a handler registered without replay receives values emitted afterwards`() {
        bus.emitState(screenKey, ScreenState("home"))

        val received = mutableListOf<String>()
        bus.addStateHandler(screenKey, replay = false) { received.add(it.name) }
        bus.emitState(screenKey, ScreenState("details"))
        bus.emitState(screenKey, ScreenState("settings"))

        assertEquals(listOf("details", "settings"), received)
    }

    @Test
    fun `a handler registered without replay against a key never emitted receives the first value`() {
        val received = mutableListOf<String>()
        bus.addStateHandler(screenKey, replay = false) { received.add(it.name) }
        bus.emitState(screenKey, ScreenState("home"))

        assertEquals(listOf("home"), received)
    }

    @Test
    fun `registering without replay leaves the retained value readable`() {
        val home = ScreenState("home")
        bus.emitState(screenKey, home)
        bus.addStateHandler(screenKey, replay = false) { }

        assertSame(home, bus.currentState(screenKey))
    }

    @Test
    fun `registering a handler again with replay does not replay once it is already registered`() {
        bus.emitState(screenKey, ScreenState("home"))

        val received = mutableListOf<String>()
        val handler = EventHandler<ScreenState> { received.add(it.name) }
        bus.addStateHandler(screenKey, replay = false, handler = handler)
        bus.addStateHandler(screenKey, handler = handler)

        assertEquals(emptyList<String>(), received)
    }

    @Test
    fun `a handler removed from a key stops receiving values`() {
        val received = AtomicInteger()
        val handler = EventHandler<ScreenState> { received.incrementAndGet() }
        bus.addStateHandler(screenKey, handler = handler)

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
    fun `current state is null for a key that has had nothing emitted against it`() {
        assertNull(bus.currentState(screenKey))
        assertNull(bus.currentState(keyBeyondCapacityFor(screenKey) { StateKey<ScreenState>() }))
    }

    @Test
    fun `current state is the value most recently emitted against the key`() {
        bus.emitState(connectionKey, Connected("home-wifi"))
        bus.emitState(connectionKey, Disconnected)

        assertSame(Disconnected, bus.currentState(connectionKey))
    }

    @Test
    fun `current state of one key is independent of another key of the same type`() {
        val home = ScreenState("home")
        bus.emitState(screenKey, home)

        assertSame(home, bus.currentState(screenKey))
        assertNull(bus.currentState(otherScreenKey))
    }

    @Test
    fun `current state survives the values growing to hold later keys`() {
        val home = ScreenState("home")
        val laterKey = keyBeyondCapacityFor(screenKey) { StateKey<ScreenState>() }
        val details = ScreenState("details")
        bus.emitState(screenKey, home)
        bus.emitState(laterKey, details)

        assertSame(home, bus.currentState(screenKey))
        assertSame(details, bus.currentState(laterKey))
    }

    @Test
    fun `current state is the value a newly registered handler is replayed`() {
        bus.emitState(screenKey, ScreenState("home"))

        val replayed = mutableListOf<ScreenState>()
        bus.addStateHandler(screenKey) { replayed.add(it) }

        assertSame(replayed.single(), bus.currentState(screenKey))
    }

    @Test
    fun `a handler that throws during state replay is reported once, like a live dispatch failure`() {
        bus.emitState(screenKey, ScreenState("home"))

        bus.addStateHandler(screenKey) { error("boom") }

        assertEquals(
            listOf(SdkErrorType.EventBusHandlerFail.toString()),
            errorHandler.sdkErrorMessages.map { it.msg },
        )
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

    @Test
    fun `a producer is created once however many times it is ensured`() {
        val created = AtomicInteger()

        repeat(3) {
            bus.ensureEventProducer(appKey) {
                created.incrementAndGet()
                AutoCloseable {}
            }
        }

        assertEquals(1, created.get())
    }

    @Test
    fun `producers of different keys are created independently`() {
        val created = mutableListOf<String>()

        bus.ensureEventProducer(appKey) {
            created.add("app")
            AutoCloseable {}
        }
        bus.ensureEventProducer(screenKey) {
            created.add("screen")
            AutoCloseable {}
        }
        bus.ensureEventProducer(appKey) {
            created.add("app again")
            AutoCloseable {}
        }

        assertEquals(listOf("app", "screen"), created)
    }

    @Test
    fun `a producer can be ensured for a key beyond the initial capacity`() {
        val created = AtomicInteger()
        val laterKey = keyBeyondCapacityFor(appKey) { EventKey<AppEvent>() }

        repeat(2) {
            bus.ensureEventProducer(laterKey) {
                created.incrementAndGet()
                AutoCloseable {}
            }
        }

        assertEquals(1, created.get())
    }

    @Test
    fun `a producer factory can register handlers and emit`() {
        val received = mutableListOf<String>()

        bus.ensureEventProducer(screenKey) {
            bus.addStateHandler(screenKey) { received.add(it.name) }
            bus.emitState(screenKey, ScreenState("home"))
            AutoCloseable {}
        }
        bus.emitState(screenKey, ScreenState("details"))

        assertEquals(listOf("home", "details"), received)
    }

    @Test
    fun `ensuring a producer from within its own factory does not create it again`() {
        val created = AtomicInteger()

        bus.ensureEventProducer(appKey) {
            created.incrementAndGet()
            bus.ensureEventProducer(appKey) {
                created.incrementAndGet()
                AutoCloseable {}
            }
            AutoCloseable {}
        }

        assertEquals(1, created.get())
    }

    @Test
    fun `a producer that throws is reported once and not created again`() {
        val attempts = AtomicInteger()

        repeat(2) {
            bus.ensureEventProducer(appKey) {
                attempts.incrementAndGet()
                error("boom")
            }
        }

        assertEquals(1, attempts.get())
        assertEquals(
            listOf(SdkErrorType.EventBusProducerFail.toString()),
            errorHandler.sdkErrorMessages.map { it.msg },
        )
    }

    @Test
    fun `concurrent callers return only once the producer has been created`() {
        val callers = 8
        val created = AtomicInteger()
        val start = CyclicBarrier(callers)
        val createdOnReturn = ConcurrentLinkedQueue<Int>()

        val threads = List(callers) {
            thread {
                start.await()
                bus.ensureEventProducer(appKey) {
                    Thread.sleep(PRODUCER_CREATION_MS)
                    created.incrementAndGet()
                    AutoCloseable {}
                }
                createdOnReturn.add(created.get())
            }
        }
        threads.forEach(Thread::join)

        assertEquals(1, created.get())
        assertEquals(List(callers) { 1 }, createdOnReturn.toList())
    }

    private companion object {
        const val PRODUCER_CREATION_MS = 50L
    }
}
