package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class EventBusTest {

    private sealed interface AppEvent

    private interface Loggable

    private open class BaseEvent : AppEvent

    private class ForegroundEvent : BaseEvent(), Loggable

    private class UnrelatedEvent

    private val logger = FakeInternalLogger(throwOnInternalError = false)
    private val bus = EventBus(logger)

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
    fun `concurrent registration and emission is safe`() {
        val threadCount = 4
        val iterations = 500
        val threadpool = Executors.newFixedThreadPool(threadCount * 2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount * 2)
        val failures = CopyOnWriteArrayList<Throwable>()
        val handlers = List(threadCount * iterations) { EventHandler<BaseEvent> { } }

        repeat(threadCount) { thread ->
            threadpool.submit {
                runCatching {
                    start.await(1, TimeUnit.SECONDS)
                    repeat(iterations) { i ->
                        val handler = handlers[thread * iterations + i]
                        bus.addHandler(BaseEvent::class.java, handler)
                        bus.removeHandler(BaseEvent::class.java, handler)
                    }
                }.onFailure(failures::add)
                done.countDown()
            }
            threadpool.submit {
                runCatching {
                    start.await(1, TimeUnit.SECONDS)
                    repeat(iterations) { bus.emit(ForegroundEvent()) }
                }.onFailure(failures::add)
                done.countDown()
            }
        }

        start.countDown()
        assertTrue("threads did not complete", done.await(30, TimeUnit.SECONDS))
        threadpool.shutdown()
        assertEquals("failures: $failures", 0, failures.size)

        // Every handler added was also removed, so a final emit must reach nothing.
        val received = AtomicInteger()
        bus.addHandler<BaseEvent> { received.incrementAndGet() }
        bus.emit(ForegroundEvent())
        assertEquals(1, received.get())
    }
}
