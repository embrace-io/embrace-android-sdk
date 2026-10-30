package io.embrace.android.embracesdk.internal.arch.store

/**
 * Persists the IDs of delivered ApplicationExitInfo records so they are not sent twice.
 */
interface DeliveredAeiStore {

    /**
     * Returns the IDs of the delivered records.
     */
    fun ids(): Set<String>

    /**
     * Records that the given ID has been delivered.
     */
    fun add(id: String)

    /**
     * Discards every delivered ID that is not in [ids].
     */
    fun retainOnly(ids: Set<String>)
}
