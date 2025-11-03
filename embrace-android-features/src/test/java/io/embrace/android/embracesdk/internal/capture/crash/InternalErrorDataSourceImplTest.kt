package io.embrace.android.embracesdk.internal.capture.crash

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeLogData
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSourceImpl
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class InternalErrorDataSourceImplTest {

    private lateinit var dataSource: InternalErrorDataSourceImpl
    private lateinit var args: FakeInstrumentationArgs

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(mockk())
        dataSource = InternalErrorDataSourceImpl(
            args
        )
    }

    @Test
    fun `handle throwable with no message`() {
        dataSource.trackInternalError(InternalErrorType.DELIVERY_SCHEDULING_FAIL, IllegalStateException())
        val data = args.destination.logEvents.single()
        val attrs = assertInternalErrorLogged(data)
        assertEquals("java.lang.IllegalStateException", attrs[ExceptionAttributes.EXCEPTION_TYPE])
        assertEquals("", attrs[ExceptionAttributes.EXCEPTION_MESSAGE])
        assertNotNull(attrs[ExceptionAttributes.EXCEPTION_STACKTRACE])
    }

    @Test
    fun `handle throwable with message`() {
        dataSource.trackInternalError(InternalErrorType.DELIVERY_SCHEDULING_FAIL, IllegalArgumentException("Whoops!"))
        val data = args.destination.logEvents.single()
        val attrs = assertInternalErrorLogged(data)
        assertEquals("java.lang.IllegalArgumentException", attrs[ExceptionAttributes.EXCEPTION_TYPE])
        assertEquals("Whoops!", attrs[ExceptionAttributes.EXCEPTION_MESSAGE])
        assertNotNull(attrs[ExceptionAttributes.EXCEPTION_STACKTRACE])
    }

    @Test
    fun `limit not exceeded`() {
        repeat(15) {
            dataSource.trackInternalError(InternalErrorType.DELIVERY_SCHEDULING_FAIL, IllegalStateException())
        }
        assertEquals(10, args.destination.logEvents.size)
    }

    private fun assertInternalErrorLogged(data: FakeLogData): Map<String, String> {
        assertEquals(LogSeverity.ERROR, data.severity)
        assertEquals("", data.message)
        assertEquals(EmbType.System.InternalError, data.schemaType.telemetryType)
        assertEquals("internal-error", data.schemaType.fixedObjectName)
        return data.schemaType.attributes()
    }
}
