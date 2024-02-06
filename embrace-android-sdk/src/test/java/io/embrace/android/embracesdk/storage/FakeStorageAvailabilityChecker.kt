package io.embrace.android.embracesdk.storage

internal class FakeStorageAvailabilityChecker : StorageAvailabilityChecker {
    override fun getAvailableBytes(): Long {
        return 1000
    }
}
