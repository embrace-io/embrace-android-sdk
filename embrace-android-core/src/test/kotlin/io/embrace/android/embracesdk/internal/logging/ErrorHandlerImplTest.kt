package io.embrace.android.embracesdk.internal.logging

import io.embrace.android.embracesdk.concurrency.runConcurrently
import io.embrace.android.embracesdk.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal class ErrorHandlerImplTest {

    private val type = SdkErrorType.DeliverySchedulingFail
    private lateinit var clock: FakeClock
    private lateinit var handler: ErrorHandlerImpl
    private lateinit var errors: MutableList<Pair<Throwable, Long>>
    private lateinit var types: MutableList<SdkErrorType>
    private val sink: ErrorRecorder = { type, throwable, timestampMs ->
        types.add(type)
        errors.add(throwable to timestampMs)
    }

    @Before
    fun setUp() {
        clock = FakeClock()
        handler = ErrorHandlerImpl(clock)
        errors = mutableListOf()
        types = mutableListOf()
    }

    @Test
    fun `guardSdkCode returns value when action succeeds`() {
        assertEquals(1, handler.guardSdkCode(type, 0) { 1 })
        handler.drain(sink)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `guardSdkCode returns default when action throws`() {
        assertEquals(0, handler.guardSdkCode(type, 0) { throw IllegalStateException() })
    }

    @Test
    fun `errors buffered until drained`() {
        val first = IllegalArgumentException("Whoops!")
        fail(first)
        val firstTime = clock.now()
        clock.tick(100)
        val second = IllegalStateException()
        fail(second)
        assertTrue(errors.isEmpty())

        handler.drain(sink)
        assertEquals(listOf(first to firstTime, second to firstTime + 100), errors)
    }

    /**
     * A thread that found the error needs buffering just as a drain takes the buffer should send
     * it directly rather than adding it to the buffer.
     */
    @Test
    fun `an error about to be buffered as the buffer is being drained is sent directly instead`() {
        val atBuffering = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holdNext = AtomicBoolean(false)
        handler = ErrorHandlerImpl(
            clock,
            beforeBuffering = {
                if (holdNext.getAndSet(false)) {
                    atBuffering.countDown()
                    release.await()
                }
            },
        )
        val buffered = IllegalStateException("buffered")
        val late = IllegalStateException("late")
        fail(buffered)
        holdNext.set(true)
        val logger = thread { fail(late) }
        assertTrue(atBuffering.await(1, TimeUnit.SECONDS))

        handler.drain(sink)
        release.countDown()
        logger.join()

        assertEquals(listOf(buffered, late), errors.map { it.first })
    }

    @Test
    fun `buffered and direct errors are recorded with their type`() {
        handler.guardSdkCode(SdkErrorType.PayloadStorageFail) { throw IllegalStateException() }
        handler.drain(sink)
        handler.guardSdkCode(SdkErrorType.SpanOperationFail) { throw IllegalStateException() }
        assertEquals(listOf(SdkErrorType.PayloadStorageFail, SdkErrorType.SpanOperationFail), types)
    }

    @Test
    fun `errors sent directly after drain`() {
        handler.drain(sink)
        fail()
        assertEquals(1, errors.size)
    }

    @Test
    fun `user code errors are not tracked`() {
        handler.drain(sink)
        handler.guardSdkCode(type) { handler.guardUserCode { throw IllegalStateException() } }
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `Ignored type is not tracked`() {
        handler.drain(sink)
        handler.guardSdkCode(SdkErrorType.Ignored) { throw IllegalStateException() }
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `guardSdkCodeEach guards each element`() {
        handler.drain(sink)
        val visited = mutableListOf<Int>()
        handler.guardSdkCodeEach(type, listOf(1, 2, 3)) {
            visited.add(it)
            check(it != 2)
        }
        assertEquals(listOf(1, 2, 3), visited)
        assertEquals(1, errors.size)
    }

    @Test
    fun `guardSdkCodeEach guards concurrent modification while iterating`() {
        handler.drain(sink)
        val items = mutableListOf(1, 2, 3)
        handler.guardSdkCodeEach(type, items) { items.add(it) }
        assertTrue(errors.single().first is ConcurrentModificationException)
    }

    @Test
    fun `errors discarded when drained without sink`() {
        fail()
        handler.drain(null)
        fail()
        handler.drain(sink)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `close drops buffered errors and stops capture`() {
        fail()
        handler.close()
        fail()
        handler.drain(sink)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `close drops sink and ignores later errors and drains`() {
        handler.drain(sink)
        handler.close()
        fail()

        val otherErrors = mutableListOf<Throwable>()
        handler.drain { _, throwable, _ -> otherErrors += throwable }
        fail()
        assertTrue(errors.isEmpty())
        assertTrue(otherErrors.isEmpty())
    }

    @Test
    fun `limit applies to buffered and sent errors until reset`() {
        repeat(8) { fail(distinctError(it)) }
        handler.drain(sink)
        repeat(5) { fail(distinctError(it + 8)) }
        assertEquals(10, errors.size)

        handler.resetLimit()
        fail(distinctError(0))
        assertEquals(11, errors.size)
    }

    @Test
    fun `repeated errors are recorded once per reset, including across a drain`() {
        fail(distinctError(0))
        handler.drain(sink)
        repeat(5) { fail(distinctError(0)) }
        assertEquals(1, errors.size)

        handler.resetLimit()
        fail(distinctError(0))
        assertEquals(2, errors.size)
    }

    @Test
    fun `repeated and uncaptured errors do not use the limit`() {
        handler.drain(sink)
        repeat(15) { fail(distinctError(0)) }
        repeat(5) {
            handler.trackSdkError(SdkErrorType.NativeReadFail, distinctError(100 + it))
            handler.trackSdkError(SdkErrorType.Ignored, distinctError(200 + it))
            handler.guardSdkCode(type) { handler.guardUserCode { throw distinctError(300 + it) } }
        }
        repeat(9) { fail(distinctError(it + 1)) }
        assertEquals(10, errors.size)
    }

    @Test
    fun `errors of a different type or exception class are not repeats`() {
        handler.drain(sink)
        val stackTrace = distinctError(0).stackTrace
        fail(IllegalStateException().apply { this.stackTrace = stackTrace })
        fail(IllegalArgumentException().apply { this.stackTrace = stackTrace })
        handler.trackSdkError(SdkErrorType.PayloadStorageFail, IllegalStateException().apply { this.stackTrace = stackTrace })
        assertEquals(3, errors.size)
    }

    @Test
    fun `racing threads record each repeated error once and never exceed the limit`() {
        handler = ErrorHandlerImpl(clock, limit = 2)
        val recorded = ConcurrentLinkedQueue<Throwable>()
        handler.drain { _, throwable, _ -> recorded.add(throwable) }
        val repeated = distinctError(-1)

        runConcurrently(threadCount = 8) { thread ->
            repeat(50) {
                fail(repeated)
                fail(distinctError(thread * 50 + it))
            }
        }
        assertEquals(2, recorded.size)
        assertEquals(2, recorded.map { it.stackTrace.single() }.distinct().size)
    }

    private var defaultErrors = 0

    /**
     * Fails with [exc], which by default is distinct from every other error so it isn't dropped as a repeat.
     */
    private fun fail(exc: Throwable = distinctError(1_000 + defaultErrors++)) {
        handler.guardSdkCode(type) { throw exc }
    }

    private fun distinctError(index: Int) = IllegalStateException().apply {
        stackTrace = arrayOf(StackTraceElement("Foo", "bar", "Foo.kt", index))
    }
}
