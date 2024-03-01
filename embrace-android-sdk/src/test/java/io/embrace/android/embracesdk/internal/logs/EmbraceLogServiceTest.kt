package io.embrace.android.embracesdk.internal.logs

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogWriter
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class EmbraceLogServiceTest {

    companion object {
        private lateinit var logWriter: FakeOpenTelemetryLogWriter
        private lateinit var metadataService: FakeMetadataService
        private lateinit var sessionIdTracker: FakeSessionIdTracker
        private lateinit var clock: Clock

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            metadataService = FakeMetadataService()
            sessionIdTracker = FakeSessionIdTracker()
        }
    }

    @Before
    fun setUp() {
        logWriter = FakeOpenTelemetryLogWriter()
        sessionIdTracker.setActiveSessionId("session-123", true)
        clock = FakeClock(123456789L)
    }

    @Test
    fun testSimpleLog() {
        val logService = getLogMessageService()

        val props = mapOf("foo" to "bar")
        logService.log("Hello world", Severity.INFO, props)
        logService.log("Warning world", Severity.WARNING, null)
        logService.log("Hello errors", Severity.ERROR, null)

        val logs = logWriter.logEvents
        assertEquals(3, logs.size)
        val first = logs[0]
        assertEquals("Hello world", first.message)
        assertNotEquals(0, first.startTimeMs)
        assertEquals(io.opentelemetry.api.logs.Severity.INFO, first.severity)
        assertEquals(io.opentelemetry.api.logs.Severity.INFO.name, first.severityText)
        assertEquals("bar", first.attributes?.get("foo"))
        assertNotNull(first.attributes?.get("emb.log_id"))
        assertEquals("session-123", first.attributes?.get("emb.session_id"))
        assertNull(first.attributes?.get("emb.exception_type"))

        val second = logs[1]
        assertEquals("Warning world", second.message)
        assertNotEquals(0, second.startTimeMs)
        assertEquals(io.opentelemetry.api.logs.Severity.WARN, second.severity)
        assertEquals(io.opentelemetry.api.logs.Severity.WARN.name, second.severityText)
        assertNotNull(second.attributes?.get("emb.log_id"))
        assertEquals("session-123", second.attributes?.get("emb.session_id"))
        assertNull(second.attributes?.get("emb.exception_type"))

        val third = logs[2]
        assertEquals("Hello errors", third.message)
        assertNotEquals(0, third.startTimeMs)
        assertEquals(io.opentelemetry.api.logs.Severity.ERROR, third.severity)
        assertEquals(io.opentelemetry.api.logs.Severity.ERROR.name, third.severityText)
        assertNotNull(third.attributes?.get("emb.log_id"))
        assertEquals("session-123", third.attributes?.get("emb.session_id"))
        assertNull(third.attributes?.get("emb.exception_type"))
    }

    @Test
    fun testExceptionLog() {
        val logService = getLogMessageService()
        val exception = NullPointerException("exception message")

        logService.logException(
            "Hello world",
            Severity.WARNING,
            LogExceptionType.NONE,
            null,
            exception.stackTrace,
            null,
            null,
            null,
            exception.javaClass.simpleName,
            exception.message,
        )

        val log = logWriter.logEvents.single()
        assertEquals("Hello world", log.message)
        assertEquals(io.opentelemetry.api.logs.Severity.WARN, log.severity)
        assertEquals(io.opentelemetry.api.logs.Severity.WARN.name, log.severityText)
        assertEquals("NullPointerException", log.attributes?.get("emb.exception_name"))
        assertEquals("exception message", log.attributes?.get("emb.exception_message"))
        assertNotNull(log.attributes?.get("emb.log_id"))
        assertEquals("session-123", log.attributes?.get("emb.session_id"))
        assertEquals("none", log.attributes?.get("emb.exception_type"))
    }

    @Test
    fun `Embrace properties can not be overriden by custom properties`() {
        val logService = getLogMessageService()
        val props = mapOf("emb.session_id" to "session-456")
        logService.log("Hello world", Severity.INFO, props)

        val log = logWriter.logEvents.single()
        assertEquals("Hello world", log.message)
        assertEquals(io.opentelemetry.api.logs.Severity.INFO, log.severity)
        assertEquals(io.opentelemetry.api.logs.Severity.INFO.name, log.severityText)
        assertNotNull(log.attributes?.get("emb.log_id"))
        assertEquals("session-123", log.attributes?.get("emb.session_id"))
    }

    private fun getLogMessageService(): EmbraceLogService {
        return EmbraceLogService(
            logWriter,
            clock,
            metadataService,
            sessionIdTracker,
            BackgroundWorker(MoreExecutors.newDirectExecutorService())
        )
    }
}
