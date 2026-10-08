package io.embrace.android.embracesdk.internal.logging

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class BufferedInternalErrorHandlerTest {

    private lateinit var handler: BufferedInternalErrorHandler
    private lateinit var destination: FakeTelemetryDestination

    @Before
    fun setUp() {
        handler = BufferedInternalErrorHandler(FakeClock())
        destination = FakeTelemetryDestination()
    }

    @Test
    fun `errors buffered until drained`() {
        handler.trackInternalError(InternalErrorType.DeliverySchedulingFail, IllegalArgumentException("Whoops!"))
        handler.trackInternalError(InternalErrorType.DeliverySchedulingFail, IllegalStateException())
        assertTrue(destination.logEvents.isEmpty())

        handler.drainTo(destination, true)
        val attrs = destination.logEvents.map { data ->
            assertEquals(LogSeverity.ERROR, data.severity)
            assertEquals("", data.message)
            assertEquals(EmbType.System.InternalError, data.schemaType.telemetryType)
            data.schemaType.attributes()
        }
        assertEquals(listOf("Whoops!", ""), attrs.map { it[ExceptionAttributes.EXCEPTION_MESSAGE] })
        assertEquals("java.lang.IllegalStateException", attrs[1][ExceptionAttributes.EXCEPTION_TYPE])
        assertNotNull(attrs[1][ExceptionAttributes.EXCEPTION_STACKTRACE])
    }

    @Test
    fun `errors sent directly after drain`() {
        handler.drainTo(destination, true)
        handler.trackInternalError(InternalErrorType.DeliverySchedulingFail, IllegalStateException())
        assertEquals(1, destination.logEvents.size)
    }

    @Test
    fun `errors discarded when disabled`() {
        handler.trackInternalError(InternalErrorType.DeliverySchedulingFail, IllegalStateException())
        handler.drainTo(destination, false)
        handler.trackInternalError(InternalErrorType.DeliverySchedulingFail, IllegalStateException())
        assertTrue(destination.logEvents.isEmpty())
    }

    @Test
    fun `limit applies to buffered and sent errors until reset`() {
        repeat(8) {
            handler.trackInternalError(InternalErrorType.DeliverySchedulingFail, IllegalStateException())
        }
        handler.drainTo(destination, true)
        repeat(5) {
            handler.trackInternalError(InternalErrorType.DeliverySchedulingFail, IllegalStateException())
        }
        assertEquals(10, destination.logEvents.size)

        handler.resetLimit()
        handler.trackInternalError(InternalErrorType.DeliverySchedulingFail, IllegalStateException())
        assertEquals(11, destination.logEvents.size)
    }
}
