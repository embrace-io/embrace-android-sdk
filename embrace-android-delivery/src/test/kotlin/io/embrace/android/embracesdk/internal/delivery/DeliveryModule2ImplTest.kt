package io.embrace.android.embracesdk.internal.delivery

import org.junit.Assert.assertNotNull
import org.junit.Test

class DeliveryModule2ImplTest {

    @Test
    fun testModule() {
        val module = DeliveryModule2Impl()
        assertNotNull(module)
        assertNotNull(module.intakeService)
        assertNotNull(module.payloadCachingService)
        assertNotNull(module.payloadResurrectionService)
        assertNotNull(module.requestExecutionService)
    }
}
