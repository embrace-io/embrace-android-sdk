package io.embrace.android.embracesdk.internal

/**
 * Exposes logic to initialize the implementing service if it's not done at instance creation time. This allows the implementing service
 * to defer expensive initialization logic to a later time when it's desirable to create the instance earlier but we don't want to take
 * the upfront performance hit of the init.
 */
public interface Initializable {

    /**
     * Explicitly initialize the service post instance creation
     */
    public fun initializeService(sdkInitStartTimeMs: Long)

    /**
     * Returns true if this service is initialized
     */
    public fun initialized(): Boolean
}
