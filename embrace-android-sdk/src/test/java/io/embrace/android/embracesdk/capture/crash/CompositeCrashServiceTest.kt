package io.embrace.android.embracesdk.capture.crash

import io.embrace.android.embracesdk.config.local.CrashHandlerLocalConfig
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.OTelRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCrashDataSource
import io.embrace.android.embracesdk.fakes.FakeCrashService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeOTelBehavior
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.payload.JsException
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class CompositeCrashServiceTest {

    private lateinit var compositeCrashService: CompositeCrashService
    private lateinit var configService: FakeConfigService
    private lateinit var logger: EmbLogger
    private lateinit var crashServiceV1: FakeCrashService
    private lateinit var crashServiceV2: FakeCrashDataSource
    private lateinit var oTelConfig: OTelRemoteConfig

    @Before
    fun setUp() {
        logger = EmbLoggerImpl()
        crashServiceV1 = FakeCrashService()
        crashServiceV2 = FakeCrashDataSource()
        oTelConfig = OTelRemoteConfig(isBetaEnabled = false)
    }

    @Test
    fun `test exception handler is registered with config option enabled`() {
        setupForHandleCrash(true)
        assert(Thread.getDefaultUncaughtExceptionHandler() is EmbraceUncaughtExceptionHandler)
    }

    @Test
    fun `test exception handler is not registered with config option disabled`() {
        setupForHandleCrash(false)
        assert(Thread.getDefaultUncaughtExceptionHandler() !is EmbraceUncaughtExceptionHandler)
    }

    @Test
    fun `test handleCrash is called on crashServiceV1 if OTelConfig is false`() {
        setupForHandleCrash(true)
        val exception = RuntimeException("Test exception")
        compositeCrashService.handleCrash(exception)
        assertEquals(null, crashServiceV2.exception)
        assertEquals(exception, crashServiceV1.exception)
    }

    @Test
    fun `test handleCrash is called on crashServiceV2 if OTelConfig is true`() {
        oTelConfig = OTelRemoteConfig(isBetaEnabled = true)
        setupForHandleCrash(true)
        val exception = RuntimeException("Test exception")
        compositeCrashService.handleCrash(exception)
        assertEquals(exception, crashServiceV2.exception)
        assertEquals(null, crashServiceV1.exception)
    }

    @Test
    fun `test logUnhandledJsException is called on crashServiceV1 if OTelConfig is false`() {
        setupForHandleCrash(true)
        val exception = JsException(
            name = "Exception name",
            message = "Exception message",
            type = "Exception type",
            stacktrace = "Exception stacktrace",
        )
        compositeCrashService.logUnhandledJsException(exception)
        assertEquals(null, crashServiceV2.jsException)
        assertEquals(exception, crashServiceV1.jsException)
    }

    @Test
    fun `test logUnhandledJsException is called on crashServiceV2 if OTelConfig is true`() {
        oTelConfig = OTelRemoteConfig(isBetaEnabled = true)
        setupForHandleCrash(true)
        val exception = JsException(
            name = "Exception name",
            message = "Exception message",
            type = "Exception type",
            stacktrace = "Exception stacktrace",
        )
        compositeCrashService.logUnhandledJsException(exception)
        assertEquals(exception, crashServiceV2.jsException)
        assertEquals(null, crashServiceV1.jsException)
    }

    private fun setupForHandleCrash(crashHandlerEnabled: Boolean) {
        configService = FakeConfigService(
            autoDataCaptureBehavior = fakeAutoDataCaptureBehavior(
                localCfg = {
                    LocalConfig(
                        "",
                        false,
                        SdkLocalConfig(crashHandler = CrashHandlerLocalConfig(crashHandlerEnabled))
                    )
                }
            ),
            oTelBehavior = fakeOTelBehavior(
                remoteCfg = {
                    RemoteConfig(oTelConfig = oTelConfig)
                }
            )
        )
        compositeCrashService = CompositeCrashService(
            { crashServiceV1 },
            { crashServiceV2 },
            configService,
            logger
        )
    }
}
