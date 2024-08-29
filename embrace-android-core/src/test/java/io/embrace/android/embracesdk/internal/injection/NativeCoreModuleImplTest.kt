package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class NativeCoreModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = createNativeCoreModule(FakeInitModule())
        assertNotNull(module.sharedObjectLoader)
        assertNotNull(module.cpuInfoDelegate)
    }
}
