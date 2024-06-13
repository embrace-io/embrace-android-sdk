package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureDataSource
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class CompositeLogServiceTest {

    private lateinit var compositeLogService: CompositeLogService
    private lateinit var v2LogService: FakeLogService
    private lateinit var networkCaptureDataSource: FakeNetworkCaptureDataSource

    @Before
    fun setUp() {
        v2LogService = FakeLogService()
        networkCaptureDataSource = FakeNetworkCaptureDataSource()
        compositeLogService = CompositeLogService(
            logService = { v2LogService }
        )
    }

    @Test
    fun `default logs to v2`() {
        logEmbraceLog()
        assertEquals(1, v2LogService.logs.size)
    }

    @Test
    fun testLogExceptionV2() {
        logTestException()
        assertEquals(1, v2LogService.exceptions.size)
        v2LogService.exceptions.single().contains("IllegalArgumentException")
    }

    @Test
    fun testFlutterExceptionV2() {
        logFlutterException()
        assertEquals(0, v2LogService.logs.size)
        assertEquals(0, v2LogService.exceptions.size)
        assertEquals(1, v2LogService.flutterExceptions.size)
    }

    @Test
    fun testWrongEventType() {
        // The log service can handle only INFO_LOG, WARNING_LOG and ERROR_LOG event types
        compositeLogService.log(
            message = "simple log",
            type = EventType.CRASH,
            logExceptionType = LogExceptionType.HANDLED,
            properties = null,
            stackTraceElements = null,
            customStackTrace = null,
            framework = Embrace.AppFramework.NATIVE,
            context = null,
            library = null,
            exceptionName = null,
            exceptionMessage = null
        )
        assertEquals(0, v2LogService.logs.size)
        assertEquals(0, v2LogService.exceptions.size)
    }

    private fun logEmbraceLog() {
        compositeLogService.log(
            message = "simple log",
            type = EventType.INFO_LOG,
            logExceptionType = LogExceptionType.NONE,
            properties = mapOf("key" to "value"),
            stackTraceElements = null,
            customStackTrace = null,
            framework = Embrace.AppFramework.NATIVE,
            context = null,
            library = null,
            exceptionName = null,
            exceptionMessage = null
        )
    }

    private fun logTestException() {
        val exception = IllegalArgumentException("bad arg")
        compositeLogService.log(
            message = "log",
            type = EventType.ERROR_LOG,
            logExceptionType = LogExceptionType.UNHANDLED,
            properties = null,
            stackTraceElements = exception.stackTrace,
            customStackTrace = null,
            framework = Embrace.AppFramework.NATIVE,
            context = null,
            library = null,
            exceptionName = exception.javaClass.name,
            exceptionMessage = exception.message
        )
    }

    private fun logFlutterException() {
        compositeLogService.log(
            message = "Dart error",
            type = EventType.ERROR_LOG,
            logExceptionType = LogExceptionType.HANDLED,
            properties = null,
            stackTraceElements = null,
            customStackTrace = null,
            framework = Embrace.AppFramework.FLUTTER,
            context = "exception context",
            library = "exception library",
            exceptionName = null,
            exceptionMessage = null
        )
    }
}
