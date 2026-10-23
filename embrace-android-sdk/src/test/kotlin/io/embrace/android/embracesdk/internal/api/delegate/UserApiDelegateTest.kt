package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.fakes.FakeUserService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@Suppress("DEPRECATION")
internal class UserApiDelegateTest {

    private lateinit var delegate: UserApiDelegate
    private lateinit var fakeUserService: FakeUserService

    @Before
    fun setUp() {
        fakeUserService = FakeUserService()
        delegate = UserApiDelegate(lazy { fakeUserService })
    }

    @Test
    fun `user identifier`() {
        delegate.setUserIdentifier("test")
        assertEquals("test", fakeUserService.id)
        delegate.clearUserIdentifier()
        assertNull(fakeUserService.id)
    }

    @Test
    fun `user email`() {
        delegate.setUserEmail("test")
        assertEquals("test", fakeUserService.email)
        delegate.clearUserEmail()
        assertNull(fakeUserService.email)
    }

    @Test
    fun `user name`() {
        delegate.setUsername("test")
        assertEquals("test", fakeUserService.name)
        delegate.clearUsername()
        assertNull(fakeUserService.name)
    }

    @Test
    fun `user payer`() {
        delegate.addUserPersona("payer")
        assertTrue(fakeUserService.personas.contains("payer"))
        delegate.clearUserPersona("payer")
        assertFalse(fakeUserService.personas.contains("test"))
    }

    @Test
    fun `user persona`() {
        delegate.addUserPersona("test")
        assertEquals(true, fakeUserService.personas.contains("test"))
        delegate.clearUserPersona("test")
        assertEquals(false, fakeUserService.personas.contains("test"))
        delegate.addUserPersona("test")
        delegate.clearAllUserPersonas()
        assertEquals(false, fakeUserService.personas.contains("test"))
    }
}
