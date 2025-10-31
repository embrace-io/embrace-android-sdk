package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeInstrumentationModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CrashModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = createCrashModule(
            FakeInitModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeAndroidServicesModule(),
            FakeInstrumentationModule(mockk())
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashDataSource)
    }
}
