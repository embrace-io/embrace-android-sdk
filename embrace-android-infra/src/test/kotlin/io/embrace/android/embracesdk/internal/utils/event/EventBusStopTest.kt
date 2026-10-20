package io.embrace.android.embracesdk.internal.utils.event

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

internal class EventBusStopTest {

    private class AppEvent

    private class ScreenState(val name: String)

    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private val bus = EventBus(errorHandler)

    private val appKey = EventKey<AppEvent>()

    private val screenKey = StateKey<ScreenState>()

    @Test
    fun `handlers registered before stop receive nothing emitted after it`() {
        val received = mutableListOf<Any>()
        bus.addHandler(appKey) { received.add(it) }
        bus.addStateHandler(screenKey) { received.add(it) }

        bus.stop()
        bus.emit(appKey, AppEvent())
        bus.emitState(screenKey, ScreenState("home"))

        assertEquals(emptyList<Any>(), received)
    }

    @Test
    fun `stop releases retained values`() {
        bus.emitState(screenKey, ScreenState("home"))

        bus.stop()

        assertNull(bus.currentState(screenKey))
    }

    @Test
    fun `handlers registered after stop receive nothing and are replayed nothing`() {
        val received = mutableListOf<Any>()
        bus.emitState(screenKey, ScreenState("home"))
        bus.stop()

        bus.addHandler(appKey) { received.add(it) }
        bus.addStateHandler(screenKey) { received.add(it) }
        bus.emit(appKey, AppEvent())
        bus.emitState(screenKey, ScreenState("details"))

        assertEquals(emptyList<Any>(), received)
    }

    @Test
    fun `values emitted after stop are not retained`() {
        val laterKey = keyBeyondCapacityFor(screenKey) { StateKey<ScreenState>() }
        bus.stop()

        bus.emitState(screenKey, ScreenState("home"))
        bus.emitState(laterKey, ScreenState("details"))

        assertNull(bus.currentState(screenKey))
        assertNull(bus.currentState(laterKey))
    }

    @Test
    fun `no producer is created after stop`() {
        val created = AtomicInteger()
        bus.stop()

        bus.ensureEventProducer(appKey) {
            created.incrementAndGet()
            AutoCloseable {}
        }

        assertEquals(0, created.get())
    }

    @Test
    fun `stopping more than once is a no-op`() {
        bus.stop()
        bus.stop()

        bus.emitState(screenKey, ScreenState("home"))

        assertNull(bus.currentState(screenKey))
    }

    @Test
    fun `stop closes every producer, most recently created first`() {
        val closed = mutableListOf<String>()
        bus.ensureEventProducer(appKey) { AutoCloseable { closed.add("app") } }
        bus.ensureEventProducer(screenKey) { AutoCloseable { closed.add("screen") } }

        bus.stop()

        assertEquals(listOf("screen", "app"), closed)
    }

    @Test
    fun `a producer is closed before the producers its factory ensured`() {
        val closed = mutableListOf<String>()
        bus.ensureEventProducer(appKey) {
            bus.ensureEventProducer(screenKey) { AutoCloseable { closed.add("screen") } }
            AutoCloseable { closed.add("app") }
        }

        bus.stop()

        assertEquals(listOf("app", "screen"), closed)
    }

    @Test
    fun `stopping more than once closes each producer once`() {
        val closed = AtomicInteger()
        bus.ensureEventProducer(appKey) { AutoCloseable { closed.incrementAndGet() } }

        bus.stop()
        bus.stop()

        assertEquals(1, closed.get())
    }

    @Test
    fun `a producer that fails to close is reported and the rest are still closed`() {
        val closed = mutableListOf<String>()
        bus.ensureEventProducer(appKey) { AutoCloseable { closed.add("app") } }
        bus.ensureEventProducer(screenKey) { AutoCloseable { error("boom") } }

        bus.stop()

        assertEquals(listOf("app"), closed)
        assertEquals(
            listOf(SdkErrorType.EventBusProducerFail.toString()),
            errorHandler.sdkErrorMessages.map { it.msg },
        )
    }

    @Test
    fun `a producer whose factory stops the bus is closed`() {
        val closed = AtomicInteger()

        bus.ensureEventProducer(appKey) {
            bus.stop()
            AutoCloseable { closed.incrementAndGet() }
        }

        assertEquals(1, closed.get())
    }

    @Test
    fun `stop returns only once a producer being created concurrently has been closed`() {
        val closed = AtomicInteger()
        val creating = CountDownLatch(1)
        val creator = thread {
            bus.ensureEventProducer(appKey) {
                creating.countDown()
                Thread.sleep(PRODUCER_CREATION_MS)
                AutoCloseable { closed.incrementAndGet() }
            }
        }
        creating.await()

        bus.stop()

        assertEquals(1, closed.get())
        creator.join()
    }

    private companion object {
        const val PRODUCER_CREATION_MS = 50L
    }
}
