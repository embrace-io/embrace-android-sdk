package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.comms.api.ApiResponseCache
import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.fakes.FakeCacheService
import io.embrace.android.embracesdk.fakes.FakeDeliveryCacheManager
import io.embrace.android.embracesdk.fakes.FakeStorageManager
import io.embrace.android.embracesdk.injection.StorageModule
import io.embrace.android.embracesdk.storage.StorageManager

internal class FakeStorageModule(
    override val cacheService: CacheService = FakeCacheService(),
    override val deliveryCacheManager: DeliveryCacheManager = FakeDeliveryCacheManager(),
    override val storageManager: StorageManager = FakeStorageManager()
) : StorageModule {

    override val cache: ApiResponseCache
        get() = throw UnsupportedOperationException()
}
