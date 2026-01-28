package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.RobolectricTest
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakeSessionTracker
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeSessionToken
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.utils.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SdkStateApiDelegateTest : RobolectricTest() {

    private lateinit var delegate: SdkStateApiDelegate
    private lateinit var logService: FakeLogService
    private lateinit var configService: FakeConfigService
    private lateinit var sessionTracker: FakeSessionTracker
    private lateinit var sdkCallChecker: SdkCallChecker
    private lateinit var logger: FakeInternalLogger

    @Before
    fun setUp() {
        logService = FakeLogService()
        configService = FakeConfigService(deviceId = Uuid.getEmbUuid())
        val moduleInitBootstrapper = ModuleInitBootstrapper(
            FakeInitModule(),
            configServiceSupplier = { _, _, _, _ ->
                configService
            },
            essentialServiceModuleSupplier = { _, _, _, _, _, _, _ ->
                FakeEssentialServiceModule()
            },
            logModuleSupplier = { _, _, _, _, _, _, _ ->
                FakeLogModule(logService = logService)
            },
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext())
        sessionTracker = moduleInitBootstrapper.essentialServiceModule.sessionTracker as FakeSessionTracker
        logger = FakeInternalLogger()
        sdkCallChecker = SdkCallChecker(logger, FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = SdkStateApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun isStarted() {
        assertTrue(delegate.isStarted)
    }

    @Test
    fun getDeviceId() {
        configService.deviceId = "foo"
        assertEquals("foo", delegate.deviceId)
    }

    @Test
    fun `device ID not returned SDK is not enabled`() {
        logger.throwOnInternalError = false
        configService.deviceId = "foo"
        sdkCallChecker.started.set(false)
        assertEquals("", delegate.deviceId)
    }

    @Test
    fun getCurrentSessionId() {
        sessionTracker.currentSession = fakeSessionToken().copy(sessionId = "test")
        assertEquals("test", delegate.currentSessionId)
    }

    @Test
    fun `last end state is invalid if SDK not enabled`() {
        sdkCallChecker.started.set(false)
        assertEquals(LastRunEndState.INVALID, delegate.lastRunEndState)
    }
}
