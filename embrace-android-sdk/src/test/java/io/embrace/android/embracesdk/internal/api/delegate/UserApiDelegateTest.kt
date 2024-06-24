package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class UserApiDelegateTest {

    private lateinit var delegate: UserApiDelegate
    private lateinit var fakeNdkService: FakeNdkService
    private lateinit var fakeUserService: FakeUserService

    @Before
    fun setUp() {
        val moduleInitBootstrapper = fakeModuleInitBootstrapper(
            nativeModuleSupplier = { _, _, _, _, _, _, _ ->
                FakeNativeModule().apply {
                    fakeNdkService = ndkService as FakeNdkService
                }
            },
            essentialServiceModuleSupplier = { _, _, _, _, _, _, _, _, _, _, _ ->
                FakeEssentialServiceModule().apply {
                    fakeUserService = userService as FakeUserService
                }
            }
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext(), Embrace.AppFramework.NATIVE, 0)
        val sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = UserApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun `user identifier`() {
        delegate.setUserIdentifier("test")
        assertEquals("test", fakeUserService.id)
        assertEquals(1, fakeNdkService.userUpdateCount)

        delegate.clearUserIdentifier()
        assertNull(fakeUserService.id)
        assertEquals(2, fakeNdkService.userUpdateCount)
    }

    @Test
    fun `user email`() {
        delegate.setUserEmail("test")
        assertEquals("test", fakeUserService.email)
        assertEquals(1, fakeNdkService.userUpdateCount)

        delegate.clearUserEmail()
        assertNull(fakeUserService.email)
        assertEquals(2, fakeNdkService.userUpdateCount)
    }

    @Test
    fun `user name`() {
        delegate.setUsername("test")
        assertEquals("test", fakeUserService.name)
        assertEquals(1, fakeNdkService.userUpdateCount)

        delegate.clearUsername()
        assertNull(fakeUserService.name)
        assertEquals(2, fakeNdkService.userUpdateCount)
    }

    @Test
    fun `user payer`() {
        delegate.setUserAsPayer()
        assertEquals(true, fakeUserService.payer)
        assertEquals(1, fakeNdkService.userUpdateCount)

        delegate.clearUserAsPayer()
        assertNull(fakeUserService.payer)
        assertEquals(2, fakeNdkService.userUpdateCount)
    }

    @Test
    fun `user persona`() {
        delegate.addUserPersona("test")
        assertEquals(true, fakeUserService.personas.contains("test"))
        assertEquals(1, fakeNdkService.userUpdateCount)

        delegate.clearUserPersona("test")
        assertEquals(false, fakeUserService.personas.contains("test"))
        assertEquals(2, fakeNdkService.userUpdateCount)

        delegate.addUserPersona("test")
        delegate.clearAllUserPersonas()
        assertEquals(false, fakeUserService.personas.contains("test"))
        assertEquals(4, fakeNdkService.userUpdateCount)
    }
}
