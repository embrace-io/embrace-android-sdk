package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeUserSessionPropertiesService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class UserSessionApiDelegateTest {

    private lateinit var delegate: UserSessionApiDelegate
    private lateinit var sessionOrchestrator: FakeSessionOrchestrator
    private lateinit var userSessionPropertiesService: FakeUserSessionPropertiesService

    @Before
    fun setUp() {
        sessionOrchestrator = FakeSessionOrchestrator()
        userSessionPropertiesService = FakeUserSessionPropertiesService()
        delegate = UserSessionApiDelegate(
            userSessionPropertiesService = userSessionPropertiesService,
            sessionOrchestrator = sessionOrchestrator,
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
        assertEquals(1, sessionOrchestrator.manualEndCount)
    }

    @Test
    fun `add user session listener`() {
        delegate.addUserSessionListener { }
        assertEquals(1, sessionOrchestrator.userSessionListeners.size)
    }

    @Test
    fun `remove user session listener unregisters the same instance despite wrapping`() {
        val listener = UserSessionListener { }
        delegate.addUserSessionListener(listener)
        assertEquals(1, sessionOrchestrator.userSessionListeners.size)

        delegate.removeUserSessionListener(listener)
        assertTrue(sessionOrchestrator.userSessionListeners.isEmpty())
    }
}
