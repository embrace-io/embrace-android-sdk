package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureDataSource
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.api.delegate.EmbraceInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.ThreadBlockageRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UiRemoteConfig
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketException

internal class EmbraceInternalInterfaceImplTest {

    private lateinit var internalImpl: EmbraceInternalInterfaceImpl
    private lateinit var embraceImpl: EmbraceImpl
    private lateinit var fakeClock: FakeClock
    private lateinit var initModule: FakeInitModule
    private lateinit var fakeConfigService: FakeConfigService

    @Before
    fun setUp() {
        embraceImpl = mockk(relaxed = true)
        fakeClock = FakeClock(currentTime = beforeObjectInitTime)
        initModule = FakeInitModule(clock = fakeClock, logger = FakeEmbLogger(false))
        fakeConfigService = FakeConfigService()
        internalImpl = EmbraceInternalInterfaceImpl(
            embraceImpl,
            initModule,
            ::FakeNetworkCaptureDataSource,
            fakeConfigService,
            initModule.openTelemetryModule.internalTracer
        )
    }

    @Test
    fun `check isNetworkSpanForwardingEnabled`() {
        assertFalse(internalImpl.isNetworkSpanForwardingEnabled())
        fakeConfigService.networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(true)
        assertTrue(internalImpl.isNetworkSpanForwardingEnabled())
    }

    @Test
    fun `check logInternalError with exception`() {
        val expectedException = SocketException()
        internalImpl.logInternalError(expectedException)
        val logger = initModule.logger as FakeEmbLogger
        checkNotNull(logger.internalErrorMessages.single().throwable)
    }

    @Test
    fun `check logInternalError with error type and message`() {
        internalImpl.logInternalError("err", "message")
        val logger = initModule.logger as FakeEmbLogger
        checkNotNull(logger.internalErrorMessages.single().throwable)
    }

    @Test
    fun `test retrieve remote config null`() {
        val cfg = internalImpl.getRemoteConfig()
        assertNull(cfg)
    }

    @Test
    fun `test retrieve remote config empty map`() {
        fakeConfigService.remoteConfig = RemoteConfig()
        val cfg = internalImpl.getRemoteConfig()
        assertEquals(emptyMap<String, String>(), cfg)
    }

    @Test
    fun `test retrieve remote config with values`() {
        fakeConfigService.remoteConfig = RemoteConfig(
            threshold = 50,
            uiConfig = UiRemoteConfig(
                taps = 25
            ),
            threadBlockageRemoteConfig = ThreadBlockageRemoteConfig(
                sampleIntervalMs = 200
            ),
            internalExceptionCaptureEnabled = true,
            disabledUrlPatterns = setOf("*.google.com")
        )
        val cfg = internalImpl.getRemoteConfig()
        val expected = mapOf(
            "threshold" to 50.0,
            "ui" to mapOf(
                "taps" to 25.0
            ),
            "anr" to mapOf(
                "interval" to 200.0
            ),
            "internal_exception_capture_enabled" to true,
            "disabled_url_patterns" to listOf("*.google.com")
        )
        assertEquals(expected, cfg)
    }

    @Test
    fun `test is config feature enabled`() {
        assertFalse(checkNotNull(internalImpl.isConfigFeatureEnabled(0.0f)))
        assertTrue(checkNotNull(internalImpl.isConfigFeatureEnabled(100.0f)))
    }

    companion object {
        private val beforeObjectInitTime = System.currentTimeMillis() - 1
    }
}
