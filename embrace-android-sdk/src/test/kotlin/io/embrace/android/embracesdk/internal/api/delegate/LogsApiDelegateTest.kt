package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.internal.arch.attrs.embExceptionHandling
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class LogsApiDelegateTest {

    private lateinit var delegate: LogsApiDelegate
    private lateinit var logService: FakeLogService

    @Before
    fun setUp() {
        logService = FakeLogService()
        val moduleInitBootstrapper = ModuleInitBootstrapper(
            FakeInitModule(),
            logModuleSupplier = { _, _, _, _, _, _, _ ->
                FakeLogModule(logService = logService)
            },
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext())

        val sdkCallChecker = SdkCallChecker(FakeInternalLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = LogsApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun logInfo() {
        delegate.logInfo("test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertNull(log.attributes[embExceptionHandling.name])
    }

    @Test
    fun logWarning() {
        delegate.logWarning("test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.WARNING, log.severity)
        assertNull(log.attributes[embExceptionHandling.name])
    }

    @Test
    fun logError() {
        delegate.logError("test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.ERROR, log.severity)
        assertNull(log.attributes[embExceptionHandling.name])
    }

    @Test
    fun logMessage() {
        delegate.logMessageImpl(severity = Severity.WARNING, message = "test")
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.WARNING, log.severity)
        assertNull(log.attributes[embExceptionHandling.name])
    }

    @Test
    fun logException() {
        delegate.logException(RuntimeException("test"))
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.ERROR, log.severity)
        assertEquals("handled", log.attributes[embExceptionHandling.name])
    }

    @Test
    fun testLogException() {
        delegate.logException(RuntimeException("test"), Severity.INFO)
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertEquals("handled", log.attributes[embExceptionHandling.name])
    }

    @Test
    fun testLogException1() {
        val props = mapOf("foo" to "bar")
        delegate.logException(RuntimeException("test"), Severity.INFO, props)
        val log = logService.loggedMessages.single()
        assertEquals("test", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertEquals("handled", log.attributes[embExceptionHandling.name])
        assertEquals("bar", log.attributes["foo"])
    }

    @Test
    fun testLogException2() {
        val props = mapOf("foo" to "bar")
        delegate.logException(RuntimeException("test"), Severity.INFO, props, "custom_message")
        val log = logService.loggedMessages.single()
        assertEquals("custom_message", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertEquals("handled", log.attributes[embExceptionHandling.name])
        assertEquals("bar", log.attributes["foo"])
    }

    @Test
    fun logCustomStacktrace() {
        val stacktrace = RuntimeException("test").stackTrace
        delegate.logCustomStacktrace(stacktrace)
        val log = logService.loggedMessages.single()
        assertEquals("", log.message)
        assertEquals(LogSeverity.ERROR, log.severity)
        assertEquals("handled", log.attributes[embExceptionHandling.name])
    }

    @Test
    fun testLogCustomStacktrace() {
        val stacktrace = RuntimeException("test").stackTrace
        delegate.logCustomStacktrace(stacktrace, Severity.INFO)
        val log = logService.loggedMessages.single()
        assertEquals("", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertEquals("handled", log.attributes[embExceptionHandling.name])
    }

    @Test
    fun testLogCustomStacktrace1() {
        val props = mapOf("foo" to "bar")
        val stacktrace = RuntimeException("test").stackTrace
        delegate.logCustomStacktrace(stacktrace, Severity.INFO, props)

        val log = logService.loggedMessages.single()
        assertEquals("", log.message)
        assertEquals(LogSeverity.INFO, log.severity)
        assertEquals("handled", log.attributes[embExceptionHandling.name])
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
        assertEquals("handled", log.attributes[embExceptionHandling.name])
        assertEquals("bar", log.attributes["foo"])
    }
}
