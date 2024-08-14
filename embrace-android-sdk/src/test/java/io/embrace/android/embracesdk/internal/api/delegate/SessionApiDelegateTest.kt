package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SessionApiDelegateTest {

    private lateinit var delegate: SessionApiDelegate
    private lateinit var orchestrator: FakeSessionOrchestrator
    private lateinit var sessionPropertiesService: FakeSessionPropertiesService

    @Before
    fun setUp() {
        val moduleInitBootstrapper = fakeModuleInitBootstrapper()
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext(), AppFramework.NATIVE, 0)
        orchestrator = moduleInitBootstrapper.sessionOrchestrationModule.sessionOrchestrator as FakeSessionOrchestrator
        sessionPropertiesService = moduleInitBootstrapper.essentialServiceModule.sessionPropertiesService as FakeSessionPropertiesService

        val sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = SessionApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun `add session property`() {
        delegate.addSessionProperty("test", "value", false)
        assertEquals("value", sessionPropertiesService.props["test"])
    }

    @Test
    fun `remove session property`() {
        delegate.addSessionProperty("test", "value", false)
        delegate.removeSessionProperty("test")
        assertNull(sessionPropertiesService.props["test"])
    }

    @Test
    fun `get session properties`() {
        sessionPropertiesService.props["key"] = "value"
        assertEquals(mapOf("key" to "value"), delegate.getSessionProperties())
    }

    @Test
    fun `end session`() {
        delegate.endSession()
        assertEquals(1, orchestrator.manualEndCount)
    }

    @Test
    fun `end session clear user info`() {
        delegate.endSession(true)
        assertEquals(1, orchestrator.manualEndCount)
    }
}
