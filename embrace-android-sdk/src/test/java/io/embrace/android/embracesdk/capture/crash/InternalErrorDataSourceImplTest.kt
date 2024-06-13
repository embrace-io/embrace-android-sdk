package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorDataSourceImpl
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.LogEventData
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

internal class InternalErrorDataSourceImplTest {

    private lateinit var dataSource: InternalErrorDataSourceImpl
    private lateinit var logWriter: FakeLogWriter
    private lateinit var logger: EmbLogger

    @Before
    fun setUp() {
        logWriter = FakeLogWriter()
        logger = EmbLoggerImpl()
        dataSource = InternalErrorDataSourceImpl(
            logWriter,
            logger
        )
    }

    @Test
    fun `handle throwable with no message`() {
        dataSource.handleInternalError(IllegalStateException())
        val data = logWriter.logEvents.single()
        val attrs = assertInternalErrorLogged(data)
        assertEquals("java.lang.IllegalStateException", attrs["exception.type"])
        assertEquals("", attrs["exception.message"])
        assertNotNull(attrs["exception.stacktrace"])
    }

    @Test
    fun `handle throwable with message`() {
        dataSource.handleInternalError(IllegalArgumentException("Whoops!"))
        val data = logWriter.logEvents.single()
        val attrs = assertInternalErrorLogged(data)
        assertEquals("java.lang.IllegalArgumentException", attrs["exception.type"])
        assertEquals("Whoops!", attrs["exception.message"])
        assertNotNull(attrs["exception.stacktrace"])
    }

    @Test
    fun `limit not exceeded`() {
        repeat(15) {
            dataSource.handleInternalError(IllegalStateException())
        }
        assertEquals(10, logWriter.logEvents.size)
    }

    private fun assertInternalErrorLogged(data: LogEventData): Map<String, String> {
        assertEquals(Severity.ERROR, data.severity)
        assertEquals("", data.message)
        assertEquals(EmbType.System.InternalError, data.schemaType.telemetryType)
        assertEquals("internal-error", data.schemaType.fixedObjectName)
        return data.schemaType.attributes()
    }
}
