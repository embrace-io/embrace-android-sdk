package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryCacheManager
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.storage.EmbraceStorageManager
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StorageModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = StorageModuleImpl(
            workerThreadModule = WorkerThreadModuleImpl(),
            initModule = InitModuleImpl(),
            coreModule = FakeCoreModule(),
        )

        assertNotNull(module.storageManager)
        assertNotNull(module.cache)
        assertNotNull(module.cacheService)
        assertNotNull(module.deliveryCacheManager)
        assertTrue(module.storageManager is EmbraceStorageManager)
        assertTrue(module.cacheService is EmbraceCacheService)
        assertTrue(module.deliveryCacheManager is EmbraceDeliveryCacheManager)
    }
}