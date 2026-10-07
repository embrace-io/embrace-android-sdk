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
     * The payload was not stored because storage is at capacity because of a transient condition that could change in time.
     */
    REJECTED,

    /**
     * The payload was not stored because building the payload or writing it to disk failed in a non-transient way.
     * Retrying can be assumed to be futile.
     */
    FAILED,
}
