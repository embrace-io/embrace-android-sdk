package io.embrace.android.embracesdk.internal.logging

import io.embrace.android.embracesdk.concurrency.runConcurrently
import io.embrace.android.embracesdk.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InterruptedIOException
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal class ErrorHandlerImplTest {

    private val type = SdkErrorType.DeliverySchedulingFail
    private lateinit var clock: FakeClock
    private lateinit var handler: ErrorHandlerImpl
    private lateinit var errors: MutableList<Pair<ThrowableSnapshot, Long>>
    private lateinit var types: MutableList<SdkErrorType>
    private val noopLogcat: (ErrorHandler.Severity, String, Throwable?) -> Unit = { _, _, _ -> }
    private val sink: ErrorRecorder = { snapshot ->
        types.add(snapshot.type)
        errors.add(snapshot.top to snapshot.timestampMs)
    }

    @Before
    fun setUp() {
        clock = FakeClock()
        handler = ErrorHandlerImpl(clock::now, noopLogcat)
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
        assertEquals(listOf(first.top() to firstTime, second.top() to firstTime + 100), errors)
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
            clock::now,
            noopLogcat,
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

        assertEquals(listOf(buffered.top(), late.top()), errors.map { it.first })
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
    fun `guards restore the interrupt status after an InterruptedException`() {
        try {
            assertEquals(0, handler.guardSdkCode(type, 0) { throw InterruptedException() })
            assertTrue(Thread.interrupted())
            handler.guardSdkCodeEach(type, listOf(1)) { throw InterruptedException() }
            assertTrue(Thread.interrupted())
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `guardSdkCodeEach guards concurrent modification while iterating`() {
        handler.drain(sink)
        val items = mutableListOf(1, 2, 3)
        handler.guardSdkCodeEach(type, items) { items.add(it) }
        assertEquals(ConcurrentModificationException::class.java.name, errors.single().first.className)
    }

    @Test
    fun `guardSdkCodeEach guards an iterator that throws`() {
        handler.drain(sink)
        val items = Iterable<Int> { throw IllegalStateException("iterator") }
        handler.guardSdkCodeEach(type, items) { }
        assertEquals("iterator", errors.single().first.message)
    }

    @Test
    fun `guardSdkCodeEach guards a sequence that throws in next`() {
        handler.drain(sink)
        val visited = mutableListOf<Int>()
        val items = sequenceOf(1, 2).map {
            check(it != 2) { "next" }
            it
        }.asIterable()
        handler.guardSdkCodeEach(type, items) { visited.add(it) }
        assertEquals(listOf(1), visited)
        assertEquals("next", errors.single().first.message)
    }

    @Test
    fun `guards return normally when trackSdkError throws`() {
        val throwing = object : ErrorHandler by handler {
            override fun trackSdkError(type: SdkErrorType, throwable: Throwable) = error("track")
        }
        assertEquals(0, throwing.guardSdkCode(type, 0) { error("action") })
        throwing.guardSdkCodeEach(type, listOf(1)) { error("action") }
        throwing.guardSdkCodeEach(type, Iterable<Int> { error("iterator") }) { }
    }

    @Test
    fun `guards restore the interrupt status after an InterruptedIOException`() {
        try {
            handler.guardSdkCode(type) { throw InterruptedIOException() }
            assertTrue(Thread.interrupted())
            handler.guardSdkCode(type) { error("not an interrupt") }
            assertFalse(Thread.interrupted())
        } finally {
            Thread.interrupted()
        }
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
    fun `close is idempotent and terminal, dropping the sink and ignoring later errors, drains and resets`() {
        handler.drain(sink)
        handler.close()
        handler.close()
        fail()

        val otherErrors = mutableListOf<SdkErrorSnapshot>()
        handler.drain { otherErrors += it }
        handler.resetLimit()
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
        handler = ErrorHandlerImpl(clock::now, noopLogcat, limit = 2)
        val recorded = ConcurrentLinkedQueue<SdkErrorSnapshot>()
        handler.drain { recorded.add(it) }
        val repeated = distinctError(-1)

        runConcurrently(threadCount = 8) { thread ->
            repeat(50) {
                fail(repeated)
                fail(distinctError(thread * 50 + it))
            }
        }
        assertEquals(2, recorded.size)
        assertEquals(2, recorded.map { it.top.frames.single() }.distinct().size)
    }

    @Test
    fun `an error tracked by the sink is recorded once as a sink failure`() {
        val first = distinctError(0)
        val raised = ArrayDeque((1..4).map(::distinctError))
        val firstRaised = raised.first()
        val recorded = mutableListOf<Pair<SdkErrorType, ThrowableSnapshot>>()
        handler.drain { (type, _, chain) ->
            recorded.add(type to chain.first())
            fail(raised.removeFirst())
            fail(raised.removeFirst())
        }
        fail(first)
        assertEquals(listOf(type to first.top(), SdkErrorType.ErrorSinkFail to firstRaised.top()), recorded)
    }

    @Test
    fun `an error thrown by the sink is recorded once as a sink failure`() {
        val first = distinctError(0)
        val thrown = ArrayDeque((1..2).map(::distinctError))
        val firstThrown = thrown.first()
        val recorded = mutableListOf<Pair<SdkErrorType, ThrowableSnapshot>>()
        handler.drain { (type, _, chain) ->
            recorded.add(type to chain.first())
            throw thrown.removeFirst()
        }
        fail(first)
        assertEquals(listOf(type to first.top(), SdkErrorType.ErrorSinkFail to firstThrown.top()), recorded)
    }

    @Test
    fun `an error raised by the sink while draining the buffer is recorded as a sink failure`() {
        val first = distinctError(0)
        val second = distinctError(1)
        fail(first)
        fail(second)
        val sinkFailures = ArrayDeque((2..3).map(::distinctError))
        val expectedFailures = sinkFailures.toList()
        val recorded = mutableListOf<Pair<SdkErrorType, ThrowableSnapshot>>()
        handler.drain { (type, _, chain) ->
            recorded.add(type to chain.first())
            if (type != SdkErrorType.ErrorSinkFail) {
                fail(sinkFailures.removeFirst())
            }
        }
        assertEquals(
            listOf(
                type to first.top(),
                SdkErrorType.ErrorSinkFail to expectedFailures[0].top(),
                type to second.top(),
                SdkErrorType.ErrorSinkFail to expectedFailures[1].top(),
            ),
            recorded,
        )
    }

    @Test
    fun `a sink failure uses the limit`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, limit = 1)
        val first = distinctError(0)
        val recorded = mutableListOf<ThrowableSnapshot>()
        handler.drain {
            recorded.add(it.top)
            error("sink failed")
        }
        fail(first)
        assertEquals(listOf(first.top()), recorded)
    }

    @Test
    fun `a repeated sink failure is not recorded again for later errors`() {
        val errors = (0..2).map(::distinctError)
        val sinkFailure = distinctError(3)
        val recorded = mutableListOf<Pair<SdkErrorType, ThrowableSnapshot>>()
        handler.drain { (type, _, chain) ->
            recorded.add(type to chain.first())
            throw sinkFailure
        }
        errors.forEach(::fail)
        assertEquals(
            listOf(
                type to errors[0].top(),
                SdkErrorType.ErrorSinkFail to sinkFailure.top(),
                type to errors[1].top(),
                type to errors[2].top(),
            ),
            recorded,
        )
    }

    @Test
    fun `a separate error thrown by the sink whilst recording a sink failure is not recorded`() {
        val errors = (0..1).map(::distinctError)
        val sinkFailure = distinctError(2)
        val retryFailure = distinctError(3)
        val recorded = mutableListOf<Pair<SdkErrorType, ThrowableSnapshot>>()
        handler.drain { (type, _, chain) ->
            recorded.add(type to chain.first())
            throw if (type == SdkErrorType.ErrorSinkFail) retryFailure else sinkFailure
        }
        errors.forEach(::fail)
        assertEquals(
            listOf(
                type to errors[0].top(),
                SdkErrorType.ErrorSinkFail to sinkFailure.top(),
                type to errors[1].top(),
            ),
            recorded,
        )
    }

    @Test
    fun `distinct sink failures follow the same limit as other errors`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, limit = 5)
        val errors = (0..3).map(::distinctError)
        val sinkFailures = ArrayDeque((4..7).map(::distinctError))
        val expectedSinkFailures = sinkFailures.toList()
        val recorded = mutableListOf<Pair<SdkErrorType, ThrowableSnapshot>>()
        handler.drain { (type, _, chain) ->
            recorded.add(type to chain.first())
            if (type != SdkErrorType.ErrorSinkFail) {
                throw sinkFailures.removeFirst()
            }
        }
        errors.forEach(::fail)

        // each distinct sink failure uses the limit, so the third is dropped and the fourth error is too
        assertEquals(
            listOf(
                type to errors[0].top(),
                SdkErrorType.ErrorSinkFail to expectedSinkFailures[0].top(),
                type to errors[1].top(),
                SdkErrorType.ErrorSinkFail to expectedSinkFailures[1].top(),
                type to errors[2].top(),
            ),
            recorded,
        )
    }

    @Test
    fun `a throwing sink does not stop later errors being recorded`() {
        val buffered = listOf(distinctError(0), distinctError(1))
        val direct = listOf(distinctError(2), distinctError(3))
        val sinkFailure = distinctError(4)
        buffered.forEach(::fail)
        val recorded = mutableListOf<ThrowableSnapshot>()
        handler.drain {
            recorded.add(it.top)
            throw sinkFailure
        }
        direct.forEach(::fail)

        // repeats of the same sink failure are dropped by the limit
        assertEquals((listOf(buffered[0], sinkFailure, buffered[1]) + direct).map { it.top() }, recorded)
    }

    @Test
    fun `a failure to log falls back to logging a warning`() {
        val logged = mutableListOf<Logged>()
        handler = ErrorHandlerImpl(clock::now, logcat = { severity, msg, throwable ->
            check(severity == ErrorHandler.Severity.WARNING) { "logcat failed" }
            logged.add(Logged(severity, msg, throwable))
        })
        handler.log("Whoops!", severity = ErrorHandler.Severity.ERROR)
        val warning = logged.single()
        assertEquals(ErrorHandler.Severity.WARNING, warning.severity)
        assertEquals("Failed to log message", warning.msg)
        assertEquals("logcat failed", warning.throwable?.message)
    }

    @Test
    fun `a throwing sink is logged as a warning`() {
        val logged = mutableListOf<Logged>()
        handler = ErrorHandlerImpl(clock::now, logcat = { severity, msg, throwable -> logged.add(Logged(severity, msg, throwable)) })
        val sinkFailure = IllegalStateException("sink failed")
        handler.drain { throw sinkFailure }
        fail()
        assertEquals(
            listOf(
                Logged(ErrorHandler.Severity.WARNING, "Failed to record SDK error ($type)", sinkFailure),
                Logged(ErrorHandler.Severity.WARNING, "Failed to record SDK error (ErrorSinkFail)", sinkFailure),
            ),
            logged,
        )
    }

    @Test
    fun `a throwing clock and logcat are tolerated, and the time falls back to the system clock`() {
        handler = ErrorHandlerImpl(now = { error("clock failed") }, logcat = { _, _, _ -> error("logcat failed") })
        val before = System.currentTimeMillis()
        repeat(5) { fail() }
        handler.log("Whoops!")
        handler.drain(sink)
        repeat(5) { fail() }
        assertEquals(List(10) { type }, types)
        assertTrue(errors.all { it.second in before..System.currentTimeMillis() })
    }

    @Test
    fun `nothing throws when every dependency throws`() {
        handler = ErrorHandlerImpl(
            now = { raise(StackOverflowError()) },
            beforeBuffering = { raise(OutOfMemoryError("buffering failed")) },
            logcat = { _, _, _ -> raise(AssertionError("logcat failed")) },
        )
        handler.log("Whoops!")
        fail()
        handler.guardSdkCode(type) { handler.guardUserCode { raise(LinkageError("user code failed")) } }
        handler.drain { raise(StackOverflowError()) }
        fail()
        handler.resetLimit()
        handler.close()
    }

    @Test
    fun `nothing throws when the sink and logcat throw, and every error reaches the sink`() {
        handler = ErrorHandlerImpl(clock::now, logcat = { _, _, _ -> error("logcat failed") })
        val buffered = listOf(distinctError(0), distinctError(1))
        val direct = listOf(distinctError(2), distinctError(3))
        buffered.forEach(::fail)
        val recorded = mutableListOf<Pair<SdkErrorType, ThrowableSnapshot>>()
        handler.drain { (type, _, chain) ->
            recorded.add(type to chain.first())
            error("sink failed")
        }
        direct.forEach(::fail)
        assertEquals((buffered + direct).map { it.top() }, recorded.filter { it.first == type }.map { it.second })
        assertEquals(1, recorded.count { it.first == SdkErrorType.ErrorSinkFail })
    }

    @Test
    fun `a sink that always throws records one sink failure per part and keeps recording later errors`() {
        val recorded = mutableListOf<SdkErrorType>()
        handler.drain {
            recorded.add(it.type)
            error("sink failed")
        }
        repeat(10) { part ->
            repeat(100) { fail(distinctError(part * 100 + it)) }
            handler.resetLimit()
        }
        assertEquals(10, recorded.count { it == SdkErrorType.ErrorSinkFail })
        assertEquals(90, recorded.count { it == type })
    }

    @Test
    fun `a sink that tracks errors from another thread is bounded by the limit`() {
        var raised = 0
        handler.drain {
            sink(it)
            val exc = distinctError(10_000 + raised++)
            thread { handler.trackSdkError(type, exc) }.join()
        }
        fail()
        assertEquals(10, errors.size)
        assertTrue(types.none { it == SdkErrorType.ErrorSinkFail })
    }

    @Test
    fun `a failure escaping the handler is logged and reported as a sink failure without stopping later errors`() {
        val logged = mutableListOf<Logged>()
        val failure = IllegalStateException("buffering failed")
        var escapes = 1
        handler = ErrorHandlerImpl(
            now = clock::now,
            logcat = { severity, msg, exc -> logged.add(Logged(severity, msg, exc)) },
            beforeBuffering = {
                if (escapes-- > 0) {
                    throw failure
                }
            },
        )
        val dropped = distinctError(0)
        val buffered = distinctError(1)
        fail(dropped)
        fail(buffered)
        handler.drain(sink)
        assertEquals(listOf(SdkErrorType.ErrorSinkFail, type), types)
        assertEquals(listOf(failure.top(), buffered.top()), errors.map { it.first })
        assertEquals(
            listOf(
                Logged(ErrorHandler.Severity.WARNING, "Failed to track SDK error", failure),
                Logged(ErrorHandler.Severity.WARNING, "Failed to track SDK error: tracked error", dropped),
            ),
            logged,
        )
    }

    @Test
    fun `a failure escaping the handler while recording is recorded straight away`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, beforeBuffering = {
            thread { handler.drain(sink) }.join()
            error("buffering failed")
        })
        fail()
        fail()
        assertEquals(listOf(SdkErrorType.ErrorSinkFail, type), types)
    }

    @Test
    fun `a failure escaping the handler whilst reporting a failure is dropped, and later errors are still recorded`() {
        val logged = mutableListOf<Logged>()
        val failures = ArrayDeque(listOf(IllegalStateException("first"), IllegalStateException("second")))
        val expected = failures.toList()
        handler = ErrorHandlerImpl(
            now = clock::now,
            logcat = { severity, msg, exc -> logged.add(Logged(severity, msg, exc)) },
            beforeBuffering = { failures.removeFirstOrNull()?.let { throw it } },
        )
        val later = distinctError(0)
        fail()
        fail(later)
        handler.drain(sink)
        assertEquals(listOf(later.top()), errors.map { it.first })
        assertEquals(expected.take(1), logged.filter { it.msg == "Failed to track SDK error" }.map { it.throwable })
    }

    @Test
    fun `a sink that throws an Error has it recorded as a sink failure`() {
        val sinkFailure = StackOverflowError()
        var throwNext = true
        handler.drain {
            sink(it)
            if (throwNext) {
                throwNext = false
                throw sinkFailure
            }
        }
        fail()
        assertEquals(listOf(type, SdkErrorType.ErrorSinkFail), types)
        assertEquals(sinkFailure.top(), errors[1].first)
    }

    @Test
    fun `a distinct sink failure is still recorded after one raised by recording a sink failure was dropped`() {
        val thrown = (0..3).map { distinctError(100 + it) }
        val toThrow = ArrayDeque(thrown)
        val recorded = mutableListOf<Pair<SdkErrorType, ThrowableSnapshot>>()
        handler.drain { (type, _, chain) ->
            recorded.add(type to chain.first())
            throw toThrow.removeFirst()
        }
        val errors = (0..1).map(::distinctError)
        errors.forEach(::fail)
        assertEquals(
            listOf(
                type to errors[0].top(),
                SdkErrorType.ErrorSinkFail to thrown[0].top(),
                type to errors[1].top(),
                SdkErrorType.ErrorSinkFail to thrown[2].top(),
            ),
            recorded,
        )
    }

    @Test
    fun `a distinct tracked sink failure is still recorded after one tracked whilst recording a sink failure was dropped`() {
        val raised = (0..3).map { distinctError(100 + it) }
        val toRaise = ArrayDeque(raised)
        val recorded = mutableListOf<Pair<SdkErrorType, ThrowableSnapshot>>()
        handler.drain { (type, _, chain) ->
            recorded.add(type to chain.first())
            fail(toRaise.removeFirst())
        }
        val errors = (0..1).map(::distinctError)
        errors.forEach(::fail)
        assertEquals(
            listOf(
                type to errors[0].top(),
                SdkErrorType.ErrorSinkFail to raised[0].top(),
                type to errors[1].top(),
                SdkErrorType.ErrorSinkFail to raised[2].top(),
            ),
            recorded,
        )
    }

    @Test
    fun `an error tracked by the sink whilst recording a buffered sink failure is dropped`() {
        val failure = IllegalStateException("buffering failed")
        var escapes = 1
        handler = ErrorHandlerImpl(clock::now, noopLogcat, beforeBuffering = {
            if (escapes-- > 0) {
                throw failure
            }
        })
        fail()
        handler.drain {
            sink(it)
            if (it.type == SdkErrorType.ErrorSinkFail) {
                fail()
            }
        }
        val later = distinctError(0)
        fail(later)
        assertEquals(listOf(failure.top(), later.top()), errors.map { it.first })
    }

    @Test
    fun `resetting the limit before a drain does not grow the buffer past the limit`() {
        repeat(100) { part ->
            repeat(10) { fail(distinctError(part * 10 + it)) }
            handler.resetLimit()
        }
        handler.drain(sink)
        assertEquals(10, errors.size)
        assertEquals((0 until 10).map { distinctError(it).top() }, errors.map { it.first })
    }

    @Test
    fun `an error dropped by a full buffer does not use the limit`() {
        repeat(10) { fail(distinctError(it)) }
        handler.resetLimit()
        fail(distinctError(10))
        handler.drain(sink)
        repeat(10) { fail(distinctError(10 + it)) }
        assertEquals((0 until 20).map { distinctError(it).top() }, errors.map { it.first })
    }

    @Test
    fun `a full buffer takes one sink failure without a reset and rejects a second`() {
        val failures = AtomicInteger(0)
        handler = ErrorHandlerImpl(clock::now, noopLogcat, beforeBuffering = {
            if (failures.getAndDecrement() > 0) {
                error("buffering failed")
            }
        })
        repeat(10) { fail(distinctError(it)) }
        failures.set(1)
        fail(distinctError(10))
        failures.set(1)
        fail(distinctError(11))

        handler.drain(sink)
        assertEquals(List(10) { type } + SdkErrorType.ErrorSinkFail, types)
    }

    @Test
    fun `a sink failure raised while closing from inside the sink is not recorded`() {
        handler.drain {
            sink(it)
            fail()
            handler.close()
        }
        fail()
        assertEquals(listOf(type), types)
    }

    @Test
    fun `a sink that closes and then throws does not record a sink failure`() {
        handler.drain {
            sink(it)
            handler.close()
            error("sink failed")
        }
        fail()
        assertEquals(listOf(type), types)
    }

    @Test(timeout = 10_000)
    fun `close waits for a sink call in progress on another thread`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, closeTimeoutMs = 10_000)
        val inSink = CountDownLatch(1)
        val release = CountDownLatch(1)
        handler.drain {
            inSink.countDown()
            release.await()
        }
        val tracker = thread(isDaemon = true) { fail() }
        val closer = thread(isDaemon = true) {
            assertTrue(inSink.await(5, TimeUnit.SECONDS))
            handler.close()
        }
        try {
            assertTrue(inSink.await(5, TimeUnit.SECONDS))
            closer.join(200)
            assertTrue(closer.isAlive)
        } finally {
            release.countDown()
        }
        closer.join(5_000)
        assertFalse(closer.isAlive)
        tracker.join(5_000)
    }

    @Test(timeout = 10_000)
    fun `close waits no longer than the timeout for a hanging sink`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, closeTimeoutMs = 100)
        val inSink = CountDownLatch(1)
        val release = CountDownLatch(1)
        handler.drain {
            inSink.countDown()
            release.await()
        }
        val tracker = thread(isDaemon = true) { fail() }
        try {
            assertTrue(inSink.await(5, TimeUnit.SECONDS))
            val start = System.nanoTime()
            handler.close()
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            assertTrue("close took ${elapsedMs}ms", elapsedMs >= 90)
        } finally {
            release.countDown()
        }
        tracker.join(5_000)
    }

    @Test
    fun `only the first close waits for a hanging sink`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, closeTimeoutMs = 200)
        val inSink = CountDownLatch(1)
        val release = CountDownLatch(1)
        handler.drain {
            inSink.countDown()
            release.await()
        }
        val tracker = thread(isDaemon = true) { fail() }
        try {
            assertTrue(inSink.await(5, TimeUnit.SECONDS))
            val first = measureMs { handler.close() }
            val second = measureMs { handler.close() }
            assertTrue("first close took ${first}ms", first in 190..2_000)
            assertTrue("second close took ${second}ms", second < 10)
        } finally {
            release.countDown()
        }
        tracker.join(5_000)
    }

    @Test
    fun `a failure starting a record leaves no record in flight and is reported`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, closeTimeoutMs = 60_000, beforeStep = failOnceAt("record"))
        handler.drain(sink)
        val lost = distinctError(0)
        fail(lost)
        fail(lost)
        assertEquals(listOf(SdkErrorType.ErrorSinkFail, type), types)
        assertEquals(lost.top(), errors[1].first)
        assertTrue(measureMs { handler.close() } < 1_000)
    }

    @Test
    fun `a failure buffering an error gives back its limiter slot`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, beforeStep = failOnceAt("buffer"))
        val lost = distinctError(0)
        fail(lost)
        fail(lost)
        handler.drain(sink)
        assertEquals(listOf(SdkErrorType.ErrorSinkFail, type), types)
        assertEquals(lost.top(), errors[1].first)
    }

    @Test
    fun `a drain that fails once is retried and the failure reported`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, beforeStep = failOnceAt("drain"))
        val buffered = distinctError(0)
        fail(buffered)
        handler.drain(sink)
        assertEquals(listOf(type, SdkErrorType.ErrorSinkFail), types)
        assertEquals(buffered.top(), errors[0].first)
    }

    @Test
    fun `a drain that fails twice keeps the buffer for a later drain`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, beforeStep = failOnceAt("drain", times = 2))
        val buffered = distinctError(0)
        fail(buffered)
        handler.drain(sink)
        assertTrue(types.isEmpty())
        handler.drain(sink)
        assertEquals(listOf(type, SdkErrorType.ErrorSinkFail), types)
        assertEquals(buffered.top(), errors[0].first)
    }

    @Test
    fun `a buffered error that fails to record is reported`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, beforeStep = failOnceAt("record"))
        fail(distinctError(0))
        handler.drain(sink)
        assertEquals(listOf(SdkErrorType.ErrorSinkFail), types)
        fail(distinctError(1))
        assertEquals(listOf(SdkErrorType.ErrorSinkFail, type), types)
    }

    @Test(timeout = 10_000)
    fun `an interrupt during the close wait ends it and keeps the interrupt status`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, closeTimeoutMs = 60_000)
        val inSink = CountDownLatch(1)
        val release = CountDownLatch(1)
        handler.drain {
            inSink.countDown()
            release.await()
        }
        val tracker = thread(isDaemon = true) { fail() }
        val stillInterrupted = AtomicBoolean(false)
        try {
            assertTrue(inSink.await(5, TimeUnit.SECONDS))
            val closer = thread(isDaemon = true) {
                handler.close()
                stillInterrupted.set(Thread.currentThread().isInterrupted)
            }
            closer.join(100)
            assertTrue(closer.isAlive)
            closer.interrupt()
            closer.join(5_000)
            assertFalse(closer.isAlive)
            assertTrue(stillInterrupted.get())
        } finally {
            release.countDown()
        }
        tracker.join(5_000)
    }

    @Test(timeout = 10_000)
    fun `close from inside the sink does not wait for itself and stops later records`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, closeTimeoutMs = 60_000)
        repeat(3) { fail(distinctError(it)) }
        handler.drain {
            sink(it)
            handler.close()
        }
        assertEquals(1, errors.size)
    }

    @Test(timeout = 10_000)
    fun `close from inside the sink still waits for sink calls on other threads`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, closeTimeoutMs = 10_000)
        val heldInSink = CountDownLatch(1)
        val releaseHeld = CountDownLatch(1)
        val closeReturned = AtomicBoolean(false)
        val holder = AtomicReference<Thread>()
        handler.drain {
            if (holder.compareAndSet(null, Thread.currentThread())) {
                heldInSink.countDown()
                releaseHeld.await()
            } else {
                handler.close()
                closeReturned.set(true)
            }
        }
        val held = thread(isDaemon = true) { fail() }
        try {
            assertTrue(heldInSink.await(5, TimeUnit.SECONDS))
            val closer = thread(isDaemon = true) { fail() }
            closer.join(200)
            assertFalse(closeReturned.get())
            releaseHeld.countDown()
            closer.join(5_000)
            assertTrue(closeReturned.get())
        } finally {
            releaseHeld.countDown()
        }
        held.join(5_000)
    }

    @Test
    fun `a buffered error holds no reference to its throwable`() {
        val ref = trackUnreferencedError()
        repeat(50) {
            if (ref.get() != null) {
                System.gc()
                Thread.sleep(10)
            }
        }
        assertNull(ref.get())
    }

    @Test
    fun `a repeated error is rejected without reading its causes`() {
        handler.trackSdkError(type, CountingError(0))
        val repeat = CountingError(0)
        handler.trackSdkError(type, repeat)
        assertEquals(1, repeat.stackTraceReads.get())
        assertEquals(0, repeat.causeReads.get())
    }

    @Test
    fun `an error past the limit is rejected without reading its stack trace`() {
        repeat(10) { fail() }
        handler.drain(sink)
        val exc = CountingError(0)
        handler.trackSdkError(type, exc)
        assertEquals(0, exc.stackTraceReads.get())
        assertEquals(10, errors.size)
    }

    @Test
    fun `an error dropped by a full buffer is not read`() {
        handler = ErrorHandlerImpl(clock::now, noopLogcat, limit = 1)
        fail()
        handler.resetLimit()
        val exc = CountingError(0)
        handler.trackSdkError(type, exc)
        assertEquals(0, exc.stackTraceReads.get())
    }

    @Test
    fun `an error tracked by the sink whilst a sink failure is held is not read`() {
        val second = CountingError(1)
        handler.drain { snapshot ->
            sink(snapshot)
            if (snapshot.type == type) {
                handler.trackSdkError(type, distinctError(0))
                handler.trackSdkError(type, second)
            }
        }
        fail()
        assertEquals(0, second.stackTraceReads.get())
        assertEquals(listOf(type, SdkErrorType.ErrorSinkFail), types)
    }

    private class CountingError(index: Int) : IllegalStateException() {
        val stackTraceReads = AtomicInteger()
        val causeReads = AtomicInteger()

        init {
            stackTrace = arrayOf(StackTraceElement("Foo", "bar", "Foo.kt", index))
        }

        override fun getStackTrace(): Array<StackTraceElement> {
            stackTraceReads.incrementAndGet()
            return super.getStackTrace()
        }

        override val cause: Throwable?
            get() {
                causeReads.incrementAndGet()
                return super.cause
            }
    }

    private fun trackUnreferencedError(): WeakReference<Throwable> {
        val exc = distinctError(0)
        handler.trackSdkError(type, exc)
        return WeakReference(exc)
    }

    private data class Logged(val severity: ErrorHandler.Severity, val msg: String, val throwable: Throwable?)

    private var defaultErrors = 0

    /**
     * Fails with [exc], which by default is distinct from every other error so it isn't dropped as a repeat.
     */
    private fun fail(exc: Throwable = distinctError(1_000 + defaultErrors++)) {
        handler.guardSdkCode(type) { throw exc }
    }

    private fun raise(error: Error): Nothing = throw error

    private fun failOnceAt(step: String, times: Int = 1): (String) -> Unit {
        var remaining = times
        return {
            if (it == step && remaining-- > 0) {
                raise(OutOfMemoryError(step))
            }
        }
    }

    private fun measureMs(action: () -> Unit): Long {
        val start = System.nanoTime()
        action()
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    }

    private fun Throwable.top() = ThrowableSnapshotter.snapshot(type, this, 0).top

    private fun distinctError(index: Int) = IllegalStateException().apply {
        stackTrace = arrayOf(StackTraceElement("Foo", "bar", "Foo.kt", index))
    }
}
