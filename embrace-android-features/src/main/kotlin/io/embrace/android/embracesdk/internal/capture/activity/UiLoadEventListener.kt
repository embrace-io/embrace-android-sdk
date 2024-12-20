package io.embrace.android.embracesdk.internal.capture.activity

/**
 * Listener to handle relevant events during the lifecycle of UI loading. Implementations should gather the data and log
 * the appropriate loading traces and spans.
 */
interface UiLoadEventListener {

    /**
     * When the given UI instance is starting to be created.
     *
     * For an Activity, it means it has entered the CREATED state of its lifecycle.
     *
     * Set [manualEnd] to true to signal that the load of this UI instance will be ended manually by calling [complete]
     */
    fun create(instanceId: Int, activityName: String, timestampMs: Long, manualEnd: Boolean)

    /**
     * When the given UI instance has been fully created and is ready to be displayed on screen.
     *
     * For an Activity, it means it is about to exit CREATED state of its lifecycle.
     */
    fun createEnd(instanceId: Int, timestampMs: Long)

    /**
     * When the given UI instance is starting to be displayed on screen
     *
     * For an Activity, it means it is about to enter the STARTED state of its lifecycle.
     *
     * Set [manualEnd] to true to signal that the load of this UI instance will be ended manually by calling [complete]
     */
    fun start(instanceId: Int, activityName: String, timestampMs: Long, manualEnd: Boolean)

    /**
     * When the given UI instance is displayed on screen and its views are ready to be rendered
     *
     * For an Activity, it means it is about to exit the STARTED state of its lifecycle.
     */
    fun startEnd(instanceId: Int, timestampMs: Long)

    /**
     * When the given UI instance is getting ready to be rendered
     *
     * For an Activity, it means it is about to enter the RESUMED state of its lifecycle.
     */
    fun resume(instanceId: Int, activityName: String, timestampMs: Long)

    /**
     * When the given UI instance is ready to start rendering.
     *
     * For an Activity, it means it is about to exit the RESUMED state of its lifecycle.
     */
    fun resumeEnd(instanceId: Int, timestampMs: Long)

    /**
     * When the given UI instance is starting to render
     *
     * For an Activity, it means when its root View is begin to render
     */
    fun render(instanceId: Int, activityName: String, timestampMs: Long)

    /**
     * When the given UI instance is has rendered its first frame
     *
     * For an Activity, it means when the first frame associated with its Window has delivered its first frame.
     */
    fun renderEnd(instanceId: Int, timestampMs: Long)

    /**
     * When the app manually signals that the load of the given UI instance is complete. This will only be respected
     * if the load is expected to be ended manually.
     */
    fun complete(instanceId: Int, timestampMs: Long)

    /**
     * When we no longer wish to observe the loading of the given UI instance. This may be called during its load
     * or after it has loaded. Calls to this for a given instance should be idempotent.
     */
    fun abandon(instanceId: Int, activityName: String, timestampMs: Long)

    /**
     * When the app is no longer in a state where it is trying to open up UI. All traces should be abandoned and
     * Any events received after this should assume the app is emerging or have emerged from a background state.
     */
    fun reset(lastInstanceId: Int)
}
