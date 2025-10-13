package io.embrace.android.embracesdk.internal.delivery.storage

enum class StorageLocation(val dir: String) {

    /**
     * A complete payload that is ready to send
     */
    PAYLOAD("embrace_payloads"),

    /**
     * An incomplete cached payload that is not ready to send
     */
    CACHE("embrace_cache"),

    /**
     * Native Embrace crash reports
     */
    NATIVE("embrace_native"),

    /**
     * Cached envelopes
     */
    ENVELOPE("embrace_envelopes")
}
