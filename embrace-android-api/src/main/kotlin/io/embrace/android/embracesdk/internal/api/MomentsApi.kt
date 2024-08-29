package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * The public API that is used to start & end moments.
 */
@InternalApi
public interface MomentsApi {

    /**
     * Starts a 'moment'. Moments are used for encapsulating particular activities within
     * the app, such as a user adding an item to their shopping cart.
     *
     * The length of time a moment takes to execute is recorded.
     *
     * @param name a name identifying the moment
     */
    public fun startMoment(name: String)

    /**
     * Starts a 'moment'. Moments are used for encapsulating particular activities within
     * the app, such as a user adding an item to their shopping cart.
     *
     * The length of time a moment takes to execute is recorded.
     *
     * @param name       a name identifying the moment
     * @param identifier an identifier distinguishing between multiple moments with the same name
     */
    public fun startMoment(name: String, identifier: String?)

    /**
     * Starts a 'moment'. Moments are used for encapsulating particular activities within
     * the app, such as a user adding an item to their shopping cart.
     *
     * The length of time a moment takes to execute is recorded
     *
     * @param name       a name identifying the moment
     * @param identifier an identifier distinguishing between multiple moments with the same name
     * @param properties custom key-value pairs to provide with the moment
     */
    public fun startMoment(
        name: String,
        identifier: String?,
        properties: Map<String, Any?>?
    )

    /**
     * Signals the end of a moment with the specified name.
     *
     * The duration of the moment is computed.
     *
     * @param name the name of the moment to end
     */
    public fun endMoment(name: String)

    /**
     * Signals the end of a moment with the specified name.
     *
     * The duration of the moment is computed.
     *
     * @param name       the name of the moment to end
     * @param identifier the identifier of the moment to end, distinguishing between moments with the same name
     */
    public fun endMoment(name: String, identifier: String?)

    /**
     * Signals the end of a moment with the specified name.
     *
     *
     * The duration of the moment is computed.
     *
     * @param name       the name of the moment to end
     * @param properties custom key-value pairs to provide with the moment
     */
    public fun endMoment(
        name: String,
        properties: Map<String, Any?>?
    )

    /**
     * Signals the end of a moment with the specified name.
     *
     * The duration of the moment is computed.
     *
     * @param name       the name of the moment to end
     * @param identifier the identifier of the moment to end, distinguishing between moments with the same name
     * @param properties custom key-value pairs to provide with the moment
     */
    public fun endMoment(
        name: String,
        identifier: String?,
        properties: Map<String, Any?>?
    )

    /**
     * Signals that the app has completed startup.
     */
    public fun endAppStartup()

    /**
     * Signals that the app has completed startup.
     *
     * @param properties properties to include as part of the startup moment
     */
    public fun endAppStartup(properties: Map<String, Any?>)
}
