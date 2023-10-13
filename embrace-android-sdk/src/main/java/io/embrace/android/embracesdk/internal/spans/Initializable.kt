package io.embrace.android.embracesdk.internal.spans

/**
 * Exposes logic to initialize the implementing service. This lives in its own interface so the users of the service will not have
 * direct access to initialize it because it's none of their business.
 */
internal interface Initializable {
    /**
     * Initialize the service so the SDK can start logging spans. Spans will not be logged by this service until this call completes.
     */
    fun initializeService(sdkInitStartTimeNanos: Long, sdkInitEndTimeNanos: Long)

    /**
     * Returns true if this service is initialized already
     */
    fun initialized(): Boolean
}
