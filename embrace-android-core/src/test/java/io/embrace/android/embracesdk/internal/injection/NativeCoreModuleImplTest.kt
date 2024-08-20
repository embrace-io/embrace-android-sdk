package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.capture.cpu.EmbraceCpuInfoDelegate
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class NativeCoreModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = createNativeCoreModule(FakeInitModule())
        assertNotNull(module.sharedObjectLoader)
        assertTrue(module.cpuInfoDelegate is EmbraceCpuInfoDelegate)
    }
}
