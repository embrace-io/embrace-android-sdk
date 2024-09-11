package io.embrace.android.embracesdk.internal.capture.activity

/**
 * The relevant stages in the lifecycle of Activities pertaining to observing the performance of their loading
 */
interface OpenEvents {

    /**
     * When a previously in-progress Activity Open trace should be abandoned, and that the component managing
     * the trace recording should prepare itself to start tracing the opening of a new Activity instance.
     */
    fun resetTrace(instanceId: Int, activityName: String, timestampMs: Long)

    /**
     * When the app is no longer in a state where it is trying to open up a new Activity
     */
    fun hibernate(instanceId: Int, activityName: String, timestampMs: Long)

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
