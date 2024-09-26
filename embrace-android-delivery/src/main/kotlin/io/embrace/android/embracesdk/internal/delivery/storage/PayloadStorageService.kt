package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.injection.SerializationAction

/**
 * Stores a completed payload to disk.
 */
interface PayloadStorageService {

    /**
     * Stores a payload
     */
    fun store(filename: String, action: SerializationAction)
}
