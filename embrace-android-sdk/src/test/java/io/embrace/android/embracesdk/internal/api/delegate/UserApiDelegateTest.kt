package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
internal class UserApiDelegateTest {

    private lateinit var delegate: UserApiDelegate
    private lateinit var fakeUserService: FakeUserService

    @Before
    fun setUp() {
        val moduleInitBootstrapper = fakeModuleInitBootstrapper(
            essentialServiceModuleSupplier = { _, _, _, _, _, _, _, _, _ ->
                FakeEssentialServiceModule().apply {
                    fakeUserService = userService as FakeUserService
                }
            }
        )
        moduleInitBootstrapper.init(
            ApplicationProvider.getApplicationContext(),
            0
        )
        val sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = UserApiDelegate(moduleInitBootstrapper, sdkCallChecker)
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
