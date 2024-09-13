package io.embrace.android.embracesdk.internal.storage

interface StorageAvailabilityChecker {
    fun getAvailableBytes(): Long
}
