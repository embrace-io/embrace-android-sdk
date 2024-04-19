package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.config.remote.OTelRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureDataSource
import io.embrace.android.embracesdk.fakes.fakeNetworkCapturedCall
import io.embrace.android.embracesdk.fakes.fakeOTelBehavior
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class CompositeLogServiceTest {

    private lateinit var otelConfig: OTelRemoteConfig
    private lateinit var compositeLogService: CompositeLogService
    private lateinit var v1LogService: FakeLogMessageService
    private lateinit var v2LogService: FakeLogService
    private lateinit var networkCaptureDataSource: FakeNetworkCaptureDataSource

    @Before
    fun setUp() {
        otelConfig = defaultOTelConfig
        val configService = FakeConfigService(
            oTelBehavior = fakeOTelBehavior(
                remoteCfg = {
                    RemoteConfig(oTelConfig = otelConfig)
                }
            )
        )
        v1LogService = FakeLogMessageService()
        v2LogService = FakeLogService()
        networkCaptureDataSource = FakeNetworkCaptureDataSource()
        compositeLogService = CompositeLogService(
            v1LogService = { v1LogService },
            v2LogService = { v2LogService },
            networkCaptureDataSource = { networkCaptureDataSource },
            configService = configService,
            logger = InternalEmbraceLogger(),
            serializer = EmbraceSerializer()
        )
    }

    @Test
    fun `default logs to v1`() {
        logEmbraceLog()
        assertEquals(0, v1LogService.loggedMessages.size)
        assertEquals(1, v2LogService.logs.size)
    }

    @Test
    fun `stable flag off logs to v1`() {
        otelConfig = stableOffOTelConfig
        logEmbraceLog()
        assertEquals(1, v1LogService.loggedMessages.size)
        assertEquals(0, v2LogService.logs.size)
    }

    @Test
    fun `stable flag on logs to v2`() {
        otelConfig = stableOnOTelConfig
        logEmbraceLog()
        assertEquals(0, v1LogService.loggedMessages.size)
        assertEquals(1, v2LogService.logs.size)
    }

    @Test
    fun testNetworkCaptureV1() {
        otelConfig = stableOffOTelConfig
        compositeLogService.logNetwork(
            fakeNetworkCapturedCall()
        )
        assertEquals(1, v1LogService.networkCalls.size)
        assertEquals(0, networkCaptureDataSource.loggedCalls.size)
    }

    @Test
    fun testNetworkCaptureV2() {
        otelConfig = stableOnOTelConfig
        compositeLogService.logNetwork(
            fakeNetworkCapturedCall()
        )
        assertEquals(0, v1LogService.networkCalls.size)
        assertEquals(1, networkCaptureDataSource.loggedCalls.size)
    }

    @Test
    fun testLogExceptionV1() {
        otelConfig = stableOffOTelConfig
        logTestException()
        assertEquals(1, v1LogService.loggedMessages.size)
        assertEquals(0, v2LogService.exceptions.size)
        v1LogService.loggedMessages.single().contains("IllegalArgumentException")
    }

    @Test
    fun testLogExceptionV2() {
        otelConfig = stableOnOTelConfig
        logTestException()
        assertEquals(0, v1LogService.loggedMessages.size)
        assertEquals(1, v2LogService.exceptions.size)
        v2LogService.exceptions.single().contains("IllegalArgumentException")
    }

    @Test
    fun testFlutterExceptionV1() {
        otelConfig = stableOffOTelConfig
        logFlutterException()
        assertEquals(1, v1LogService.loggedMessages.size)
        assertEquals(0, v2LogService.logs.size)
    }

    @Test
    fun testFlutterExceptionV2() {
        otelConfig = stableOnOTelConfig
        logFlutterException()
        assertEquals(0, v1LogService.loggedMessages.size)
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
        assertEquals(0, v1LogService.loggedMessages.size)
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

    companion object {
        private val defaultOTelConfig = OTelRemoteConfig()
        private val stableOnOTelConfig = OTelRemoteConfig(isStableEnabled = true)
        private val stableOffOTelConfig = OTelRemoteConfig(isStableEnabled = false)
    }
}
