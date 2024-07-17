package io.embrace.android.embracesdk.storage

import io.embrace.android.embracesdk.internal.storage.StorageAvailabilityChecker

internal class FakeStorageAvailabilityChecker : StorageAvailabilityChecker {
    override fun getAvailableBytes(): Long {
        return 1000
    }
}
