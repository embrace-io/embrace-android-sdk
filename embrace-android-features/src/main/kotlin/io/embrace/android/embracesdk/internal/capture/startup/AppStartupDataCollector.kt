package io.embrace.android.embracesdk.internal.capture.startup

import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode

/**
 * Collects relevant information during app startup to be used to produce telemetry about the startup workflow.
 *
 * Due to differences in behaviour between platform versions and various startup scenarios, you cannot assume that these methods
 * will be invoked in any order or at all. Implementations need to take into account that fact when using the underlying data.
 */
interface AppStartupDataCollector {
    /**
     *  Set the time when the application object initialization was started
     */
    fun applicationInitStart(timestampMs: Long? = null)

    /**
     *  Set the time when the application object initialization has finished
     */
    fun applicationInitEnd(timestampMs: Long? = null)

    /**
     * Set the time the first activity was detected to have started, irrespective of whether it should be used for startup
     */
    fun firstActivityInit(timestampMs: Long? = null)

    /**
     * Set the time just prior to the creation of the Activity whose rendering will denote the end of the startup workflow
     */
    fun startupActivityPreCreated(timestampMs: Long? = null)

    /**
     * Set the time for the start of the initialization of the Activity whose rendering will denote the end of the startup workflow
     */
    fun startupActivityInitStart(timestampMs: Long? = null)

    /**
     * Set the time just after the creation of the Activity whose rendering will denote the end of the startup workflow
     */
    fun startupActivityPostCreated(timestampMs: Long? = null)

    /**
     * Set the time for the end of the initialization of the Activity whose rendering will denote the end of the startup workflow
     */
    fun startupActivityInitEnd(timestampMs: Long? = null)

    /**
     * Set the time for when the startup Activity begins to render as well as its name
     */
    fun startupActivityResumed(
        activityName: String,
        collectionCompleteCallback: (() -> Unit)? = null,
        timestampMs: Long? = null,
    )

    /**
     * Set the time for when the startup Activity has finished rendering its first frame as well as its name
     */
    fun firstFrameRendered(
        activityName: String,
        collectionCompleteCallback: (() -> Unit)? = null,
        timestampMs: Long? = null,
    )

    /**
     * Set an arbitrary time interval during startup that is of note
     */
    fun addTrackedInterval(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        attributes: Map<String, String> = emptyMap(),
        events: List<EmbraceSpanEvent> = emptyList(),
        errorCode: ErrorCode? = null,
    )

    /**
     * Add custom attribute to the root span of the trace logged for app startup
     */
    fun addAttribute(key: String, value: String)
}
