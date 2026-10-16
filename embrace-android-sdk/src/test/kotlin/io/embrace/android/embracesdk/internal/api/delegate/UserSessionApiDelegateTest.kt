package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeUserSessionPropertiesService
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeUserSessionOrchestrationModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class UserSessionApiDelegateTest {

    private lateinit var delegate: UserSessionApiDelegate
    private lateinit var fakeModule: FakeUserSessionOrchestrationModule
    private lateinit var userSessionPropertiesService: FakeUserSessionPropertiesService

    @Before
    fun setUp() {
        fakeModule = FakeUserSessionOrchestrationModule()
        val essentialServiceModule = FakeEssentialServiceModule()
        userSessionPropertiesService = essentialServiceModule.userSessionPropertiesService
        delegate = UserSessionApiDelegate(
            userSessionPropertiesService = userSessionPropertiesService,
            sessionOrchestrator = fakeModule.sessionOrchestrator,
        )
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
    fun `add user session listener`() {
        delegate.addUserSessionListener { }
        assertEquals(1, (fakeModule.sessionOrchestrator as FakeSessionOrchestrator).userSessionListeners.size)
    }

    @Test
    fun `remove user session listener unregisters the same instance despite wrapping`() {
        val orchestrator = fakeModule.sessionOrchestrator as FakeSessionOrchestrator
        val listener = UserSessionListener { }
        delegate.addUserSessionListener(listener)
        assertEquals(1, orchestrator.userSessionListeners.size)

        delegate.removeUserSessionListener(listener)
        assertTrue(orchestrator.userSessionListeners.isEmpty())
    }
}
