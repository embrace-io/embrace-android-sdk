package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceDeliveryCacheManager
import io.embrace.android.embracesdk.internal.injection.createStorageModuleSupplier
import io.embrace.android.embracesdk.internal.storage.EmbraceStorageService
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StorageModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val initModule = FakeInitModule()
        val coreModule = FakeCoreModule()
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
        assertTrue(module.deliveryCacheManager is EmbraceDeliveryCacheManager)
    }
}
