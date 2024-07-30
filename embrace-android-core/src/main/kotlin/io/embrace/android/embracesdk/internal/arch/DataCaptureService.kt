package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener

/**
 * Represents a service that captures data passively, usually in response to callbacks, system
 * events, or just at regular intervals. This interface currently has two contracts:
 *
 * 1. The service must reset all its state (including captured data) when [cleanCollections] is called.
 * 2. The service must return all the data it has captured so far when [getCapturedData] is called.
 *
 * This approach avoids needing any knowledge about session boundaries or what the session payload
 * looks like. It also simplifies testing.
 */
public interface DataCaptureService<T> : MemoryCleanerListener {

    /**
     * Returns a representation of all the data that has already been captured so far.
     *
     * This does NOT mean that implementations should go capture data - they should just return
     * what has already been captured, if anything.
     */
    public fun getCapturedData(): T
}
