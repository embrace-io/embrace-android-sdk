package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class LogsApiDelegateTest {

    private lateinit var delegate: LogsApiDelegate
    private lateinit var logService: FakeLogService
    private lateinit var orchestrator: FakeSessionOrchestrator

    @Before
    fun setUp() {
        logService = FakeLogService()
        val moduleInitBootstrapper = fakeModuleInitBootstrapper(
            logModuleSupplier = { _, _, _, _, _, _, _, _ -> FakeLogModule(logService = logService) }
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext(), AppFramework.NATIVE, 0)
        orchestrator = moduleInitBootstrapper.sessionOrchestrationModule.sessionOrchestrator as FakeSessionOrchestrator

        val sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = LogsApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun logInfo() {
        delegate.logInfo("test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(Severity.INFO, log.severity)
        assertEquals(LogExceptionType.NONE, log.logExceptionType)
    }

    @Test
    fun logWarning() {
        delegate.logWarning("test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(Severity.WARNING, log.severity)
        assertEquals(LogExceptionType.NONE, log.logExceptionType)
    }

    @Test
    fun logError() {
        delegate.logError("test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(Severity.ERROR, log.severity)
        assertEquals(LogExceptionType.NONE, log.logExceptionType)
    }

    @Test
    fun logMessage() {
        delegate.logMessageImpl(severity = Severity.WARNING, message = "test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(Severity.WARNING, log.severity)
        assertEquals(LogExceptionType.NONE, log.logExceptionType)
    }

    @Test
    fun logException() {
        delegate.logException(RuntimeException("test"))
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(Severity.ERROR, log.severity)
        assertEquals(LogExceptionType.HANDLED, log.logExceptionType)
    }

    @Test
    fun testLogException() {
        delegate.logException(RuntimeException("test"), Severity.INFO)
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(Severity.INFO, log.severity)
        assertEquals(LogExceptionType.HANDLED, log.logExceptionType)
    }

    @Test
    fun testLogException1() {
        val props = mapOf("foo" to "bar")
        delegate.logException(RuntimeException("test"), Severity.INFO, props)
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(Severity.INFO, log.severity)
        assertEquals(LogExceptionType.HANDLED, log.logExceptionType)
        assertEquals(props, log.properties)
    }

    @Test
    fun testLogException2() {
        val props = mapOf("foo" to "bar")
        delegate.logException(RuntimeException("test"), Severity.INFO, props, "custom_message")
        val log = logService.loggedMessages.single()
        assertEquals("custom_message", log.message)
        assertEquals(Severity.INFO, log.severity)
        assertEquals(LogExceptionType.HANDLED, log.logExceptionType)
        assertEquals(props, log.properties)
    }

    @Test
    fun logCustomStacktrace() {
        val stacktrace = RuntimeException("test").stackTrace
        delegate.logCustomStacktrace(stacktrace)
        val log = logService.loggedMessages.single()
        assertEquals("", log.message)
        assertEquals(Severity.ERROR, log.severity)
        assertEquals(LogExceptionType.HANDLED, log.logExceptionType)
    }

    @Test
    fun testLogCustomStacktrace() {
        val stacktrace = RuntimeException("test").stackTrace
        delegate.logCustomStacktrace(stacktrace, Severity.INFO)
        val log = logService.loggedMessages.single()
        assertEquals("", log.message)
        assertEquals(Severity.INFO, log.severity)
        assertEquals(LogExceptionType.HANDLED, log.logExceptionType)
    }

    @Test
    fun testLogCustomStacktrace1() {
        val props = mapOf("foo" to "bar")
        val stacktrace = RuntimeException("test").stackTrace
        delegate.logCustomStacktrace(stacktrace, Severity.INFO, props)

        val log = logService.loggedMessages.single()
        assertEquals("", log.message)
        assertEquals(Severity.INFO, log.severity)
        assertEquals(LogExceptionType.HANDLED, log.logExceptionType)
        assertEquals(props, log.properties)
    }

    @Test
    fun testLogCustomStacktrace2() {
        val props = mapOf("foo" to "bar")
        val stacktrace = RuntimeException("test").stackTrace
        delegate.logCustomStacktrace(stacktrace, Severity.INFO, props, "my message")

        val log = logService.loggedMessages.single()
        assertEquals("my message", log.message)
        assertEquals(Severity.INFO, log.severity)
        assertEquals(LogExceptionType.HANDLED, log.logExceptionType)
        assertEquals(props, log.properties)
    }
}
