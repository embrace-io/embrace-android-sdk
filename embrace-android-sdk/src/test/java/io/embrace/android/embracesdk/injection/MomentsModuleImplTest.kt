package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createMomentsModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class MomentsModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = createMomentsModule(
            FakeInitModule(),
            FakeWorkerThreadModule(),
            FakeEssentialServiceModule(),
            FakeConfigModule(),
            FakePayloadSourceModule(),
            FakeDeliveryModule(),
            0
        )
        assertNotNull(module.eventService)
    }
}
