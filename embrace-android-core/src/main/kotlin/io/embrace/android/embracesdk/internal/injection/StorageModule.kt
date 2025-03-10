package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.storage.StorageService

/**
 * Contains dependencies that are used to store data in the device's storage.
 */
interface StorageModule {
    val storageService: StorageService
}
