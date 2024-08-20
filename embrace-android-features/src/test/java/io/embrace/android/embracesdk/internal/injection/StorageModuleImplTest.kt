package io.embrace.android.embracesdk.internal.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.internal.storage.EmbraceStorageService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class StorageModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule()
        val coreModule = CoreModuleImpl(RuntimeEnvironment.getApplication(), FakeEmbLogger())
        val module = createStorageModuleSupplier(
            initModule = initModule,
            coreModule = coreModule,
            workerThreadModule = FakeWorkerThreadModule(),
        )

        assertNotNull(module.storageService)
        assertNotNull(module.cache)
        assertNotNull(module.cacheService)
        assertNotNull(module.deliveryCacheManager)
        assertTrue(module.storageService is EmbraceStorageService)
        assertTrue(module.cacheService is EmbraceCacheService)
        assertNotNull(module.deliveryCacheManager)
    }
}
