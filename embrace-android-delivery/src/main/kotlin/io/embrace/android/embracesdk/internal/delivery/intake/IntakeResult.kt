package io.embrace.android.embracesdk.internal.delivery.intake

/**
 * The result of handing a payload to [IntakeService].
 */
enum class IntakeResult {

    /**
     * The payload was successfully stored.
     */
    STORED,

    /**
     * The service did not attempt to store the payload due to a possibly-transient condition that is not directly related
     * to a failure in attempting to construct or store the payload such as the service being sealed.
     */
    NOT_ATTEMPTED,

    /**
     * Storing the payload failed in a way that may not recur on a retry, such as storage being at capacity.
     */
    RETRYABLE_FAILURE,

    /**
     * Storing the payload failed in a way that we assume would recur on future retries.
     */
    PERMANENT_FAILURE,
}
