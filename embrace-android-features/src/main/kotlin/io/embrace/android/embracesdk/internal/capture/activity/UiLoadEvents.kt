package io.embrace.android.embracesdk.internal.capture.activity

/**
 * Relevant events in during the lifecycle of UI loading. Listeners to these events should gather the data and log
 * the appropriate loading traces and spans given the associated Activity.
 */
interface UiLoadEvents {

    /**
     * When we no longer wish to observe the loading of the given Activity instance. This may be called during its load
     * or after it has loaded. Calls to this for a given Activity instance is idempotent
     */
    fun abandon(instanceId: Int, activityName: String, timestampMs: Long)

    /**
     * When the app is no longer in a state where it is trying to open up a new Activity
     */
    fun reset(instanceId: Int)

    /**
     * When the given Activity is entering the CREATE stage of its lifecycle.
     */
    fun create(instanceId: Int, activityName: String, timestampMs: Long)

    /**
     * When the given Activity has exited the CREATE stage of its lifecycle.
     */
    fun createEnd(instanceId: Int, timestampMs: Long)

    /**
     * When the given Activity is entering the START stage of its lifecycle.
     */
    fun start(instanceId: Int, activityName: String, timestampMs: Long)

    /**
     * When the given Activity has exited the START stage of its lifecycle.
     */
    fun startEnd(instanceId: Int, timestampMs: Long)

    /**
     * When the given Activity is entering the RESUME stage of its lifecycle.
     */
    fun resume(instanceId: Int, activityName: String, timestampMs: Long)

    /**
     * When the given Activity has exited the RESUME stage of its lifecycle.
     */
    fun resumeEnd(instanceId: Int, timestampMs: Long)

    /**
     * When the given Activity's first UI frame starts to be rendered.
     */
    fun render(instanceId: Int, activityName: String, timestampMs: Long)

    /**
     * When the given Activity's first UI frame has been displayed.
     */
    fun renderEnd(instanceId: Int, timestampMs: Long)
}
