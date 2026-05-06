package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.FakeUserSessionPropertiesService
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeUserSessionOrchestrationModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class UserSessionApiDelegateTest {

    private lateinit var delegate: UserSessionApiDelegate
    private lateinit var fakeModule: FakeUserSessionOrchestrationModule
    private lateinit var sdkCallChecker: SdkCallChecker
    private lateinit var userSessionPropertiesService: FakeUserSessionPropertiesService
    private lateinit var logger: FakeInternalLogger

    @Before
    fun setUp() {
        fakeModule = FakeUserSessionOrchestrationModule()
        val moduleInitBootstrapper = ModuleInitBootstrapper(
            FakeInitModule(),
            essentialServiceModuleSupplier = { _, _, _, _, _, _, _, _ ->
                FakeEssentialServiceModule()
            },
            userSessionOrchestrationModuleSupplier = { _, _, _, _, _, _, _, _, _, _, _ ->
                fakeModule
            }
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext())
        userSessionPropertiesService =
            moduleInitBootstrapper.essentialServiceModule.userSessionPropertiesService as FakeUserSessionPropertiesService
        logger = FakeInternalLogger()
        sdkCallChecker = SdkCallChecker(logger, FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = UserSessionApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun `cannot modify session properties when SDK is not enabled`() {
        logger.throwOnInternalError = false
        sdkCallChecker.started.set(false)
        assertFalse(delegate.addUserSessionProperty("test", "value", PropertyScope.USER_SESSION))
        assertFalse(delegate.removeUserSessionProperty("test"))
        assertEquals(0, (fakeModule.sessionOrchestrator as FakeSessionOrchestrator).stateChangeCount)
        delegate.endUserSession()
        assertEquals(0, (fakeModule.sessionOrchestrator as FakeSessionOrchestrator).manualEndCount)
    }

    @Test
    fun `add session property`() {
        delegate.addUserSessionProperty("test", "value", PropertyScope.USER_SESSION)
        assertEquals("value", userSessionPropertiesService.props["test"])
    }

    @Test
    fun `remove session property`() {
        delegate.addUserSessionProperty("test", "value", PropertyScope.USER_SESSION)
        delegate.removeUserSessionProperty("test")
        assertNull(userSessionPropertiesService.props["test"])
    }

    @Test
    fun `end session`() {
        delegate.endUserSession()
        assertEquals(1, (fakeModule.sessionOrchestrator as FakeSessionOrchestrator).manualEndCount)
    }

    @Test
    fun `add user session listener when SDK started`() {
        delegate.addUserSessionListener { }
        assertEquals(1, (fakeModule.sessionOrchestrator as FakeSessionOrchestrator).userSessionListeners.size)
    }

    @Test
    fun `add user session listener when SDK not started`() {
        logger.throwOnInternalError = false
        sdkCallChecker.started.set(false)
        delegate.addUserSessionListener { }
        assertTrue((fakeModule.sessionOrchestrator as FakeSessionOrchestrator).userSessionListeners.isEmpty())
    }
}
