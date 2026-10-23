package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LogsApiDelegateTest {

    private lateinit var delegate: LogsApiDelegate
    private lateinit var logService: FakeLogService
    private lateinit var telemetryService: FakeInternalTelemetryService

    @Before
    fun setUp() {
        logService = FakeLogService()
        telemetryService = FakeInternalTelemetryService()
        val initServices = TestInitServices(fakeTelemetryService = telemetryService, otelSdkMode = OtelSdkMode.COMPAT)
        delegate = LogsApiDelegate(
            logService = lazy { logService },
            attachmentService = AttachmentService(),
            errorHandler = initServices.errorHandler,
            payloadStore = null,
            serializer = initServices.jsonSerializer,
            telemetryService = telemetryService,
        )
    }

    @Test
    fun logInfo() {
        delegate.logInfo("test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertNull(log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
    }

    @Test
    fun logWarning() {
        delegate.logWarning("test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.WARNING, log.severity)
        assertNull(log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
    }

    @Test
    fun logError() {
        delegate.logError("test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.ERROR, log.severity)
        assertNull(log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
    }

    @Test
    fun logMessage() {
        delegate.logMessageImpl(severity = Severity.WARNING, message = "test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.WARNING, log.severity)
        assertNull(log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
    }

    @Test
    fun logException() {
        delegate.logException(RuntimeException("test"))
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.ERROR, log.severity)
        assertEquals("handled", log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
    }

    @Test
    fun testLogException() {
        delegate.logException(RuntimeException("test"), Severity.INFO)
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertEquals("handled", log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
    }

    @Test
    fun testLogException1() {
        val props = mapOf("foo" to "bar")
        delegate.logException(RuntimeException("test"), Severity.INFO, props)
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertEquals("handled", log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
        assertEquals("bar", log.attributes["foo"])
    }

    @Test
    fun testLogException2() {
        val props = mapOf("foo" to "bar")
        delegate.logException(RuntimeException("test"), Severity.INFO, props, "custom_message")
        val log = logService.loggedMessages.single()
        assertEquals("custom_message", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertEquals("handled", log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
        assertEquals("bar", log.attributes["foo"])
    }

    @Test
    fun logCustomStacktrace() {
        val stacktrace = RuntimeException("test").stackTrace
        delegate.logCustomStacktrace(stacktrace)
        val log = logService.loggedMessages.single()
        assertEquals("", log.message)
        assertEquals(LogSeverity.ERROR, log.severity)
        assertEquals("handled", log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
    }

    @Test
    fun testLogCustomStacktrace() {
        val stacktrace = RuntimeException("test").stackTrace
        delegate.logCustomStacktrace(stacktrace, Severity.INFO)
        val log = logService.loggedMessages.single()
        assertEquals("", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertEquals("handled", log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
    }

    @Test
    fun testLogCustomStacktrace1() {
        val props = mapOf("foo" to "bar")
        val stacktrace = RuntimeException("test").stackTrace
        delegate.logCustomStacktrace(stacktrace, Severity.INFO, props)

        val log = logService.loggedMessages.single()
        assertEquals("", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertEquals("handled", log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
        assertEquals("bar", log.attributes["foo"])
    }

    @Test
    fun testLogCustomStacktrace2() {
        val props = mapOf("foo" to "bar")
        val stacktrace = RuntimeException("test").stackTrace
        delegate.logCustomStacktrace(stacktrace, Severity.INFO, props, "my message")

        val log = logService.loggedMessages.single()
        assertEquals("my message", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertEquals("handled", log.attributes[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING])
        assertEquals("bar", log.attributes["foo"])
    }

    @Test
    fun `attachment too large tracks drop`() {
        val largeAttachment = ByteArray(2 * 1024 * 1024) // 2MB, exceeds 1MB limit
        delegate.logMessage("test", Severity.INFO, emptyMap(), largeAttachment)

        assertEquals(1, telemetryService.appliedLimits.size)
        assertEquals("attachment" to AppliedLimitType.DROP, telemetryService.appliedLimits.first())
    }

    @Test
    fun `attachment over session limit tracks drop`() {
        val attachment = ByteArray(100) // Small valid attachment
        // Log 6 attachments (limit is 5 per session)
        repeat(6) {
            delegate.logMessage("test", Severity.INFO, emptyMap(), attachment)
        }

        // The 6th attachment should be dropped
        assertEquals(1, telemetryService.appliedLimits.size)
        assertEquals("attachment" to AppliedLimitType.DROP, telemetryService.appliedLimits.first())
    }

    @Test
    fun `valid attachment does not track drop`() {
        val attachment = ByteArray(100) // Small valid attachment
        delegate.logMessage("test", Severity.INFO, emptyMap(), attachment)

        assertTrue(telemetryService.appliedLimits.isEmpty())
    }
}
