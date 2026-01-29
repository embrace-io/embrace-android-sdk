package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SessionApiDelegateTest {

    private lateinit var delegate: SessionApiDelegate
    private lateinit var orchestrator: FakeSessionOrchestrator
    private lateinit var sdkCallChecker: SdkCallChecker
    private lateinit var sessionPropertiesService: FakeSessionPropertiesService
    private lateinit var logger: FakeInternalLogger

    @Before
    fun setUp() {
        val moduleInitBootstrapper = ModuleInitBootstrapper(
            FakeInitModule(),
            essentialServiceModuleSupplier = { _, _, _, _, _, _, _ ->
                FakeEssentialServiceModule()
            },
            sessionOrchestratorSupplier = { _, _, _, _, _, _, _, _, _, _ ->
                FakeSessionOrchestrator()
            }
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext())
        orchestrator = moduleInitBootstrapper.sessionOrchestrator as FakeSessionOrchestrator
        sessionPropertiesService =
            moduleInitBootstrapper.essentialServiceModule.sessionPropertiesService as FakeSessionPropertiesService
        logger = FakeInternalLogger()
        sdkCallChecker = SdkCallChecker(logger, FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = SessionApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun `cannot modify session properties when SDK is not enabled`() {
        logger.throwOnInternalError = false
        sdkCallChecker.started.set(false)
        assertFalse(delegate.addSessionProperty("test", "value", false))
        assertFalse(delegate.removeSessionProperty("test"))
        assertEquals(0, orchestrator.stateChangeCount)
        delegate.endSession()
        assertEquals(0, orchestrator.manualEndCount)
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
