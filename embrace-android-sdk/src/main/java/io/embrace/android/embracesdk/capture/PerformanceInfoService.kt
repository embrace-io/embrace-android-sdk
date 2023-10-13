package io.embrace.android.embracesdk.capture

import io.embrace.android.embracesdk.payload.PerformanceInfo

/**
 * Generates performance payloads using combined performance metrics from the device, including:
 *
 *  * CPU
 *  * Power
 *  * ANR intervals
 *  * Network calls
 *  * Memory usage
 *  * Disk usage
 *  * Signal strength
 *  * Connection quality class
 *
 */
internal interface PerformanceInfoService {

    /**
     * Gets the device performance information payload for an event. This is sent only with the
     * following events, because they have a corresponding start event so a time window can
     * be computed for capturing the performance information:
     *
     *  * LATE
     *  * END
     *  * INTERRUPT
     *
     *
     * @param startTime the start time of the performance information to retrieve
     * @param endTime   the end time of the performance information to retrieve
     * @return the performance information
     */
    fun getPerformanceInfo(
        startTime: Long,
        endTime: Long,
        coldStart: Boolean
    ): PerformanceInfo

    /**
     * Gets the device performance information payload to send with the session message. This
     * indicates activity on the device during that particular session, and is used to build a
     * timeline of events. This is like the [PerformanceInfo], but contains a timeline
     * of network events.
     *
     * @param sessionStart         the start time of the session
     * @param sessionLastKnownTime the last known time of the session
     * @param coldStart            whether the session was a cold start or not
     * @param receivedTermination  whether the session received a termination or not
     * @param isNotCachedSession  whether the process is terminating due to a crash or it ended up cleanly and is not forceQuit
     * @return the performance information for the session
     */
    fun getSessionPerformanceInfo(
        sessionStart: Long,
        sessionLastKnownTime: Long,
        coldStart: Boolean,
        receivedTermination: Boolean?
    ): PerformanceInfo
}
