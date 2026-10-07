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

    private val logger = FakeInternalLogger(throwOnInternalError = false)
    private val bus = EventBus(logger)

    private val appKey = EventKey<AppEvent>()

    private val otherAppKey = EventKey<AppEvent>()

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
    fun `an event is not replayed to a handler registered afterwards`() {
        bus.emit(appKey, ForegroundEvent())

        val received = AtomicInteger()
        bus.addHandler(appKey) { received.incrementAndGet() }

        assertEquals(0, received.get())
    }
}
