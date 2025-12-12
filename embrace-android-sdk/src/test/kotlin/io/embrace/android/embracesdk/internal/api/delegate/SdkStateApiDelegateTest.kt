package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionTracker
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeSessionZygote
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
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
internal class SdkStateApiDelegateTest {

    private lateinit var delegate: SdkStateApiDelegate
    private lateinit var logService: FakeLogService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var sessionTracker: FakeSessionTracker
    private lateinit var sdkCallChecker: SdkCallChecker
    private lateinit var logger: FakeEmbLogger

    @Before
    fun setUp() {
        logService = FakeLogService()
        val moduleInitBootstrapper = ModuleInitBootstrapper(
            FakeInitModule(),
            coreModuleSupplier = { _, _ ->
                FakeCoreModule().apply {
                    preferencesService.deviceIdentifier = Uuid.getEmbUuid()
                }
            },
            essentialServiceModuleSupplier = { _, _, _, _, _, _, _ ->
                FakeEssentialServiceModule()
            },
            logModuleSupplier = { _, _, _, _, _, _, _ ->
                FakeLogModule(logService = logService)
            },
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext())
        preferencesService = moduleInitBootstrapper.coreModule.preferencesService as FakePreferenceService
        sessionTracker = moduleInitBootstrapper.essentialServiceModule.sessionTracker as FakeSessionTracker
        logger = FakeEmbLogger()
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
        preferencesService.deviceIdentifier = "foo"
        assertEquals("foo", delegate.deviceId)
    }

    @Test
    fun `device ID not returned SDK is not enabled`() {
        logger.throwOnInternalError = false
        preferencesService.deviceIdentifier = "foo"
        sdkCallChecker.started.set(false)
        assertEquals("", delegate.deviceId)
    }

    @Test
    fun getCurrentSessionId() {
        sessionTracker.currentSession = fakeSessionZygote().copy(sessionId = "test")
        assertEquals("test", delegate.currentSessionId)
    }

    @Test
    fun `last end state is invalid if SDK not enabled`() {
        sdkCallChecker.started.set(false)
        assertEquals(LastRunEndState.INVALID, delegate.lastRunEndState)
    }
}
