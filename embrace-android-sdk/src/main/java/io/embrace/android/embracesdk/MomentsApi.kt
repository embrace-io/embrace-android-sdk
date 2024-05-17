package io.embrace.android.embracesdk

/**
 * The public API that is used to start & end moments.
 */
internal interface MomentsApi {

    /**
     * Starts a 'moment'. Moments are used for encapsulating particular activities within
     * the app, such as a user adding an item to their shopping cart.
     *
     * The length of time a moment takes to execute is recorded.
     *
     * @param name a name identifying the moment
     */
    fun startMoment(name: String)

    /**
     * Starts a 'moment'. Moments are used for encapsulating particular activities within
     * the app, such as a user adding an item to their shopping cart.
     *
     * The length of time a moment takes to execute is recorded.
     *
     * @param name       a name identifying the moment
     * @param identifier an identifier distinguishing between multiple moments with the same name
     */
    fun startMoment(name: String, identifier: String?)

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
    fun startMoment(
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
    fun endMoment(name: String)

    /**
     * Signals the end of a moment with the specified name.
     *
     * The duration of the moment is computed.
     *
     * @param name       the name of the moment to end
     * @param identifier the identifier of the moment to end, distinguishing between moments with the same name
     */
    fun endMoment(name: String, identifier: String?)

    /**
     * Signals the end of a moment with the specified name.
     *
     *
     * The duration of the moment is computed.
     *
     * @param name       the name of the moment to end
     * @param properties custom key-value pairs to provide with the moment
     */
    fun endMoment(
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
    fun endMoment(
        name: String,
        identifier: String?,
        properties: Map<String, Any?>?
    )

    /**
     * Signals that the app has completed startup.
     */
    fun endAppStartup()

    /**
     * Signals that the app has completed startup.
     *
     * @param properties properties to include as part of the startup moment
     */
    fun endAppStartup(properties: Map<String, Any?>)
}
