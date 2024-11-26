package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class NativeCoreModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule()
        val module = createNativeCoreModule(
            initModule,
            createCoreModule(mockk(relaxed = true), initModule),
            FakePayloadSourceModule(),
            FakeWorkerThreadModule(),
            FakeConfigModule(),
            FakeStorageModule(),
            FakeEssentialServiceModule(),
        )
        assertNotNull(module.sharedObjectLoader)
        assertNotNull(module.symbolService)
        assertNotNull(module.processor)
        assertNotNull(module.delegate)
        assertNull(module.nativeCrashHandlerInstaller)
    }
}
