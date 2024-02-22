package io.embrace.android.embracesdk.internal.logs

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
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
        private lateinit var logger: FakeOpenTelemetryLogger
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
        logger = FakeOpenTelemetryLogger()
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

        val logs = logger.builders
        assertEquals(3, logs.size)
        val first = logs[0]
        assertEquals("Hello world", first.body)
        assertNotEquals(0, first.timestampEpochNanos)
        assertEquals(io.opentelemetry.api.logs.Severity.INFO, first.severity)
        assertEquals(io.opentelemetry.api.logs.Severity.INFO.name, first.severityText)
        assertEquals("bar", first.attributes["foo"])
        assertNotNull(first.attributes["emb.event_id"])
        assertEquals("session-123", first.attributes["emb.session_id"])
        assertNull(first.attributes["emb.exception_type"])

        val second = logs[1]
        assertEquals("Warning world", second.body)
        assertNotEquals(0, second.timestampEpochNanos)
        assertEquals(io.opentelemetry.api.logs.Severity.WARN, second.severity)
        assertEquals(io.opentelemetry.api.logs.Severity.WARN.name, second.severityText)
        assertNotNull(second.attributes["emb.event_id"])
        assertEquals("session-123", second.attributes["emb.session_id"])
        assertNull(second.attributes["emb.exception_type"])

        val third = logs[2]
        assertEquals("Hello errors", third.body)
        assertNotEquals(0, third.timestampEpochNanos)
        assertEquals(io.opentelemetry.api.logs.Severity.ERROR, third.severity)
        assertEquals(io.opentelemetry.api.logs.Severity.ERROR.name, third.severityText)
        assertNotNull(third.attributes["emb.event_id"])
        assertEquals("session-123", third.attributes["emb.session_id"])
        assertNull(third.attributes["emb.exception_type"])
    }

    @Test
    fun testExceptionLog() {
        val logService = getLogMessageService()
        val exception = NullPointerException("exception message")

        logService.logException(
            "Hello world",
            LogExceptionType.NONE,
            null,
            exception.stackTrace,
            null,
            null,
            null,
            exception.javaClass.simpleName,
            exception.message,
        )

        val log = logger.builders.single()
        assertEquals("Hello world", log.body)
        assertEquals(io.opentelemetry.api.logs.Severity.ERROR, log.severity)
        assertEquals(io.opentelemetry.api.logs.Severity.ERROR.name, log.severityText)
        assertEquals("NullPointerException", log.attributes["emb.exception_name"])
        assertEquals("exception message", log.attributes["emb.exception_message"])
        assertNotNull(log.attributes["emb.event_id"])
        assertEquals("session-123", log.attributes["emb.session_id"])
        assertEquals("none", log.attributes["emb.exception_type"])
    }

    private fun getLogMessageService(): EmbraceLogService {
        return EmbraceLogService(
            logger,
            clock,
            metadataService,
            sessionIdTracker,
            BackgroundWorker(MoreExecutors.newDirectExecutorService())
        )
    }
}
