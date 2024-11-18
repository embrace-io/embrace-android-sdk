package io.embrace.android.embracesdk.internal.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class StorageModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule()
        val coreModule = CoreModuleImpl(RuntimeEnvironment.getApplication(), initModule)
        val module = createStorageModuleSupplier(
            initModule = initModule,
            coreModule = coreModule,
            workerThreadModule = FakeWorkerThreadModule(),
        )

        assertNotNull(module.storageService)
        assertNotNull(module.cacheService)
        assertNotNull(module.deliveryCacheManager)
        assertNotNull(module.storageService)
        assertNotNull(module.cacheService)
        assertNotNull(module.deliveryCacheManager)
    }
}
