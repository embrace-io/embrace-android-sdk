package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryCacheManager
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.storage.EmbraceStorageService
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StorageModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = InitModuleImpl()
        val coreModule = FakeCoreModule()
        val module = StorageModuleImpl(
            initModule = initModule,
            coreModule = coreModule,
            workerThreadModule = WorkerThreadModuleImpl(initModule),
        )

        assertNotNull(module.storageService)
        assertNotNull(module.cache)
        assertNotNull(module.cacheService)
        assertNotNull(module.deliveryCacheManager)
        assertTrue(module.storageService is EmbraceStorageService)
        assertTrue(module.cacheService is EmbraceCacheService)
        assertTrue(module.deliveryCacheManager is EmbraceDeliveryCacheManager)
    }
}
