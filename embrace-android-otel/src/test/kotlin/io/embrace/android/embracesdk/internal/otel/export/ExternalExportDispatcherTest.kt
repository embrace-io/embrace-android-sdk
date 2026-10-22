package io.embrace.android.embracesdk.internal.otel.export

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class ExternalExportDispatcherTest {

    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var dispatcher: ExternalExportDispatcher

    @Before
    fun setUp() {
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        dispatcher = ExternalExportDispatcher(errorHandler) { Dispatchers.Unconfined }
    }

    @Test
    fun `exporter failure is not tracked`() {
        dispatcher.dispatch(listOf(1)) { throw IllegalStateException() }
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `sdk failure is tracked`() {
        val exc = IllegalStateException()
        dispatcher.dispatch(throwingList(exc)) {}
        val msg = errorHandler.sdkErrorMessages.single()
        assertEquals(SdkErrorType.OtelExternalExportFail.toString(), msg.msg)
        assertEquals(exc, msg.throwable)
    }

    @Test
    fun `io and vm failures are not tracked`() {
        dispatcher.dispatch(throwingList(IOException())) {}
        dispatcher.dispatch(throwingList(OutOfMemoryError())) {}
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `shutdown lets a suspending export finish`() {
        val dispatcher = ExternalExportDispatcher(errorHandler)
        val exported = CountDownLatch(1)
        dispatcher.dispatch(listOf(1)) {
            delay(50)
            exported.countDown()
        }
        dispatcher.shutdown()
        assertTrue(exported.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `shutdown cancels a suspending export that outlives the timeout`() {
        val dispatcher = ExternalExportDispatcher(errorHandler, shutdownTimeoutMs = 10)
        val cancelled = CountDownLatch(1)
        dispatcher.dispatch(listOf(1)) {
            try {
                awaitCancellation()
            } catch (exc: CancellationException) {
                cancelled.countDown()
                throw exc
            }
        }
        dispatcher.shutdown()
        assertTrue(cancelled.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `shutdown interrupts a blocking export that outlives the timeout`() {
        val dispatcher = ExternalExportDispatcher(errorHandler, shutdownTimeoutMs = 10)
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        dispatcher.dispatch(listOf(1)) {
            try {
                started.countDown()
                CountDownLatch(1).await()
            } catch (ignored: InterruptedException) {
                interrupted.countDown()
            }
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        dispatcher.shutdown()
        assertTrue(interrupted.await(5, TimeUnit.SECONDS))
    }

    private fun throwingList(exc: Throwable): List<Int> = object : AbstractList<Int>() {
        override val size: Int get() = throw exc
        override fun get(index: Int): Int = throw exc
    }
}
