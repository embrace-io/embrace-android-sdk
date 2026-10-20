package io.embrace.android.embracesdk.internal.otel.export

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

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

    private fun throwingList(exc: Throwable): List<Int> = object : AbstractList<Int>() {
        override val size: Int get() = throw exc
        override fun get(index: Int): Int = throw exc
    }
}
