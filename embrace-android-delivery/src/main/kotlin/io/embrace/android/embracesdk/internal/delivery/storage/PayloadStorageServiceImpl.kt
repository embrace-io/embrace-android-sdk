package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.comms.delivery.CacheService
import io.embrace.android.embracesdk.internal.injection.SerializationAction

internal class PayloadStorageServiceImpl(
    private val cacheService: CacheService
) : PayloadStorageService {

    override fun store(filename: String, action: SerializationAction) {
        cacheService.cachePayload(filename, action)
    }
}
