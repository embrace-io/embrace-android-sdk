package io.embrace.android.embracesdk.internal.store

/**
 * Groups the store writes made on the calling thread into as few disk writes as the backing storage allows.
 */
fun interface StoreWriteBatcher {

    /**
     * Runs [action], coalescing the store writes it makes on the calling thread. Nested calls join the outermost one,
     * whose writes are flushed when it returns, even if [action] throws.
     */
    fun batch(action: () -> Unit)
}
