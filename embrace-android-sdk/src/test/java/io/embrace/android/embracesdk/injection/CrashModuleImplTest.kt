package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeCustomerLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataContainerModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeSessionModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CrashModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = CrashModuleImpl(
            InitModuleImpl(),
            FakeCoreModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
            FakeDeliveryModule(),
            FakeNativeModule(),
            FakeSessionModule(),
            FakeAnrModule(),
            FakeDataContainerModule(),
            FakeAndroidServicesModule(),
            FakeCustomerLogModule(),
        )
        assertNotNull(module.lastRunCrashVerifier)
        assertNotNull(module.crashService)
        assertNotNull(module.automaticVerificationExceptionHandler)
    }
}
