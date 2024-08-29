package io.embrace.android.embracesdk.internal.session

public interface MemoryCleanerListener {

    /**
     * Clean collections in memory when a session ends occurs.
     */
    public fun cleanCollections()
}
