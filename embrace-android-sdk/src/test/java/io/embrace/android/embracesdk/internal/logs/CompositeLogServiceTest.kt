package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class CompositeLogServiceTest {

    private var sessionConfig = SessionRemoteConfig()
    private lateinit var compositeLogService: CompositeLogService
    private lateinit var v1LogService: FakeLogMessageService
    private lateinit var v2LogService: FakeLogService

    @Before
    fun setUp() {
        val configService = FakeConfigService(
            sessionBehavior = fakeSessionBehavior(
                remoteCfg = {
                    RemoteConfig(sessionConfig = sessionConfig)
                }
            )
        )
        v1LogService = FakeLogMessageService()
        v2LogService = FakeLogService()
        compositeLogService = CompositeLogService(
            v1LogService = v1LogService,
            v2LogService = v2LogService,
            configService = configService
        )
    }

    @Test
    fun testLogV2() {
        sessionConfig = SessionRemoteConfig(useV2Payload = true)
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
        assertEquals(1, v2LogService.loggedMessages.size)
    }

    @Test
    fun testLogExceptionV2() {
        sessionConfig = SessionRemoteConfig(useV2Payload = true)
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
        assertEquals(1, v2LogService.loggedExceptions.size)
    }

    @Test
    fun testWrongEventType() {
        // The log service can handle only INFO_LOG, WARNING_LOG and ERROR_LOG event types
        sessionConfig = SessionRemoteConfig(useV2Payload = true)
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
        assertEquals(0, v2LogService.loggedMessages.size)
        assertEquals(0, v2LogService.loggedExceptions.size)
    }
}
