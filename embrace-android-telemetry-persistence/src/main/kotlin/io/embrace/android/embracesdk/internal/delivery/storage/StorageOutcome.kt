package io.embrace.android.embracesdk.internal.delivery.storage

/**
 * The result of an attempt to store a payload in [FileStorageService]. Only attempts that return [STORED]
 * writes the payload to disk.
 */
enum class StorageOutcome {

    /**
     * The payload was successfully written to disk with the given name.
     * */
    STORED,

    /**
     * The payload was not stored due to a transient condition.
     */
    REJECTED,

    /**
     * The payload was not stored due to an error when attempting to create it. Retrying is assumed to be futile.
     */
    FAILED,
}
