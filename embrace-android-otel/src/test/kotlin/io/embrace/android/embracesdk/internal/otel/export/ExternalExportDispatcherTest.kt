package io.embrace.android.embracesdk.internal.otel.export

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

internal class ExternalExportDispatcherTest {

    private lateinit var logger: FakeInternalLogger
    private lateinit var dispatcher: ExternalExportDispatcher

    @Before
    fun setUp() {
        logger = FakeInternalLogger(throwOnInternalError = false)
        dispatcher = ExternalExportDispatcher(logger) { Dispatchers.Unconfined }
    }

    @Test
    fun `exporter failure is not tracked`() {
        dispatcher.dispatch(listOf(1)) { throw IllegalStateException() }
        assertTrue(logger.internalErrorMessages.isEmpty())
    }

    @Test
    fun `sdk failure is tracked`() {
        val exc = IllegalStateException()
        dispatcher.dispatch(throwingList(exc)) {}
        val msg = logger.internalErrorMessages.single()
        assertEquals(InternalErrorType.OtelExternalExportFail.toString(), msg.msg)
        assertEquals(exc, msg.throwable)
    }

    @Test
    fun `io and vm failures are not tracked`() {
        dispatcher.dispatch(throwingList(IOException())) {}
        dispatcher.dispatch(throwingList(OutOfMemoryError())) {}
        assertTrue(logger.internalErrorMessages.isEmpty())
    }

    private fun throwingList(exc: Throwable): List<Int> = object : AbstractList<Int>() {
        override val size: Int get() = throw exc
        override fun get(index: Int): Int = throw exc
    }
}
