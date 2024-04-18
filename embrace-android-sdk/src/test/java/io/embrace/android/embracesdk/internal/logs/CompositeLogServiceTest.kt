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

    private var oTelConfig = OTelRemoteConfig()
    private lateinit var compositeLogService: CompositeLogService
    private lateinit var v1LogService: FakeLogMessageService
    private lateinit var v2LogService: FakeLogService
    private lateinit var networkCaptureDataSource: FakeNetworkCaptureDataSource

    @Before
    fun setUp() {
        val configService = FakeConfigService(
            oTelBehavior = fakeOTelBehavior(
                remoteCfg = {
                    RemoteConfig(oTelConfig = oTelConfig)
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
    fun testLogV1() {
        compositeLogService.log(
            message = "simple log",
            type = EventType.INFO_LOG,
            logExceptionType = LogExceptionType.NONE,
            properties = null,
            stackTraceElements = null,
            customStackTrace = null,
            framework = Embrace.AppFramework.NATIVE,
            context = null,
            library = null,
            exceptionName = null,
            exceptionMessage = null
        )
        assertEquals(1, v1LogService.loggedMessages.size)
        assertEquals(0, v2LogService.logs.size)
    }

    @Test
    fun testLogV2() {
        oTelConfig = OTelRemoteConfig(isBetaEnabled = true)
        compositeLogService.log(
            message = "simple log",
            type = EventType.INFO_LOG,
            logExceptionType = LogExceptionType.NONE,
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
        assertEquals(1, v2LogService.logs.size)
    }

    @Test
    fun testNetworkCaptureV1() {
        oTelConfig = OTelRemoteConfig(isBetaEnabled = false)
        compositeLogService.logNetwork(
            fakeNetworkCapturedCall())
        assertEquals(1, v1LogService.networkCalls.size)
        assertEquals(0, networkCaptureDataSource.loggedCalls.size)
    }

    @Test
    fun testNetworkCaptureV2() {
        oTelConfig = OTelRemoteConfig(isBetaEnabled = true)
        compositeLogService.logNetwork(
            fakeNetworkCapturedCall())
        assertEquals(0, v1LogService.networkCalls.size)
        assertEquals(1, networkCaptureDataSource.loggedCalls.size)
    }

    @Test
    fun testLogExceptionV1() {
        compositeLogService.log(
            message = "simple log",
            type = EventType.INFO_LOG,
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
        assertEquals(1, v1LogService.loggedMessages.size)
        assertEquals(0, v2LogService.exceptions.size)
    }

    @Test
    fun testLogExceptionV2() {
        oTelConfig = OTelRemoteConfig(isBetaEnabled = true)
        compositeLogService.log(
            message = "simple log",
            type = EventType.INFO_LOG,
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
        assertEquals(1, v2LogService.exceptions.size)
    }

    @Test
    fun testFlutterExceptionV1() {
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
        assertEquals(1, v1LogService.loggedMessages.size)
        assertEquals(0, v2LogService.logs.size)
    }

    @Test
    fun testFlutterExceptionV2() {
        oTelConfig = OTelRemoteConfig(isBetaEnabled = true)
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
        assertEquals(0, v1LogService.loggedMessages.size)
        assertEquals(0, v2LogService.logs.size)
        assertEquals(0, v2LogService.exceptions.size)
        assertEquals(1, v2LogService.flutterExceptions.size)
    }

    @Test
    fun testWrongEventType() {
        // The log service can handle only INFO_LOG, WARNING_LOG and ERROR_LOG event types
        oTelConfig = OTelRemoteConfig(isBetaEnabled = true)
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

    @Test
    fun `exception properly in v2`() {
        val exception = IllegalArgumentException("bad arg")
        oTelConfig = OTelRemoteConfig(isBetaEnabled = true)
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
        assertEquals(0, v1LogService.loggedMessages.size)
        v2LogService.exceptions.single().contains("IllegalArgumentException")
    }
}
