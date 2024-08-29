package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.api.ApiResponseCache
import io.embrace.android.embracesdk.internal.comms.delivery.CacheService
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.internal.storage.StorageService

/**
 * Contains dependencies that are used to store data in the device's storage.
 */
public interface StorageModule {
    public val storageService: StorageService
    public val cache: ApiResponseCache
    public val cacheService: CacheService
    public val deliveryCacheManager: DeliveryCacheManager
}
