package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.LastRunEndState
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.session.id.SessionData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SdkStateApiDelegateTest {

    private lateinit var delegate: SdkStateApiDelegate
    private lateinit var logService: FakeLogService
    private lateinit var orchestrator: FakeSessionOrchestrator
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var sdkCallChecker: SdkCallChecker
    private lateinit var logger: FakeEmbLogger

    @Before
    fun setUp() {
        logService = FakeLogService()
        val moduleInitBootstrapper = fakeModuleInitBootstrapper(
            logModuleSupplier = { _, _, _, _, _, _, _, _ -> FakeLogModule(logService = logService) }
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext(), AppFramework.NATIVE, 0)
        orchestrator = moduleInitBootstrapper.sessionOrchestrationModule.sessionOrchestrator as FakeSessionOrchestrator
        preferencesService = moduleInitBootstrapper.androidServicesModule.preferencesService as FakePreferenceService
        sessionIdTracker = moduleInitBootstrapper.essentialServiceModule.sessionIdTracker as FakeSessionIdTracker
        logger = FakeEmbLogger()
        sdkCallChecker = SdkCallChecker(logger, FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = SdkStateApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun getCustomAppId() {
        sdkCallChecker.started.set(false)
        delegate.setAppId("abcde")
        assertEquals("abcde", delegate.customAppId)
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
        sessionIdTracker.sessionData = SessionData("test", true)
        assertEquals("test", delegate.currentSessionId)
    }

    @Test
    fun `last end state is invalid if SDK not enabled`() {
        sdkCallChecker.started.set(false)
        assertEquals(LastRunEndState.INVALID, delegate.lastRunEndState)
    }
}
