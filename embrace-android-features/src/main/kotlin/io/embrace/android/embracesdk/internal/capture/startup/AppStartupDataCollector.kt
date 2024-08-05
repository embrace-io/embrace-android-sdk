package io.embrace.android.embracesdk.internal.capture.startup

/**
 * Collects relevant information during app startup to be used to produce telemetry about the startup workflow.
 *
 * Due to differences in behaviour between platform versions and various startup scenarios, you cannot assume that these methods
 * will be invoked in any order or at all. Implementations need to take into account that fact when using the underlying data.
 */
public interface AppStartupDataCollector {
    /**
     *  Set the time when the application object initialization was started
     */
    public fun applicationInitStart(timestampMs: Long? = null)

    /**
     *  Set the time when the application object initialization has finished
     */
    public fun applicationInitEnd(timestampMs: Long? = null)

    /**
     * Set the time just prior to the creation of the Activity whose rendering will denote the end of the startup workflow
     */
    public fun startupActivityPreCreated(timestampMs: Long? = null)

    /**
     * Set the time for the start of the initialization of the Activity whose rendering will denote the end of the startup workflow
     */
    public fun startupActivityInitStart(timestampMs: Long? = null)

    /**
     * Set the time just after the creation of the Activity whose rendering will denote the end of the startup workflow
     */
    public fun startupActivityPostCreated(timestampMs: Long? = null)

    /**
     * Set the time for the end of the initialization of the Activity whose rendering will denote the end of the startup workflow
     */
    public fun startupActivityInitEnd(timestampMs: Long? = null)

    /**
     * Set the time for when the startup Activity begins to render as well as its name
     */
    public fun startupActivityResumed(activityName: String, timestampMs: Long? = null)

    /**
     * Set the time for when the startup Activity has finished rendering its first frame as well as its name
     */
    public fun firstFrameRendered(activityName: String, timestampMs: Long? = null)

    /**
     * Set an arbitrary time interval during startup that is of note
     */
    public fun addTrackedInterval(name: String, startTimeMs: Long, endTimeMs: Long)
}
