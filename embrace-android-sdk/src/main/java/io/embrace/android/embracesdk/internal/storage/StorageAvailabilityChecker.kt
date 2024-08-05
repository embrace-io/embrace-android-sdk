package io.embrace.android.embracesdk.internal.storage

internal interface StorageAvailabilityChecker {
    fun getAvailableBytes(): Long
}
