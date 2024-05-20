package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.fakes.injection.FakeCustomerLogModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SdkStateApiDelegateTest {

    private lateinit var delegate: SdkStateApiDelegate
    private lateinit var logMessageService: FakeLogMessageService
    private lateinit var orchestrator: FakeSessionOrchestrator
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var sdkCallChecker: SdkCallChecker

    @Before
    fun setUp() {
        logMessageService = FakeLogMessageService()
        val moduleInitBootstrapper = fakeModuleInitBootstrapper(
            customerLogModuleSupplier = { _, _, _, _, _, _, _, _ -> FakeCustomerLogModule(logMessageService = logMessageService) }
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext(), Embrace.AppFramework.NATIVE, 0)
        orchestrator = moduleInitBootstrapper.sessionModule.sessionOrchestrator as FakeSessionOrchestrator
        preferencesService = moduleInitBootstrapper.androidServicesModule.preferencesService as FakePreferenceService
        sessionIdTracker = moduleInitBootstrapper.essentialServiceModule.sessionIdTracker as FakeSessionIdTracker

        sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
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
        assertTrue(delegate.isStarted())
    }

    @Test
    fun getDeviceId() {
        preferencesService.deviceIdentifier = "foo"
        assertEquals("foo", delegate.getDeviceId())
    }

    @Test
    fun getCurrentSessionId() {
        sessionIdTracker.sessionId = "test"
        assertEquals("test", delegate.getCurrentSessionId())
    }
}
