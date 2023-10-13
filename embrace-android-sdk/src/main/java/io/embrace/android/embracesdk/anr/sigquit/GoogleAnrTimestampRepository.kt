package io.embrace.android.embracesdk.anr.sigquit

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

// we never want anything the SDK gathers to be unbounded, so we set this limit on the number of Google ANR
// timestamps we collect even though it is unlikely that this limit will be reached.
private const val MAX_GOOGLE_ANR_COUNT: Long = 50

/* We don't want to miss ANRs too close to the start or end time of the session. We'd rather get more ANRs than
 necessary than to miss an ANR. We extend the range of time in which we are searching ANRs by this time margin.*/
private const val TIME_MARGIN = 5L

internal class GoogleAnrTimestampRepository(private val logger: InternalEmbraceLogger) {
    private val googleAnrTimestamps = ArrayList<Long>()

    fun add(timestamp: Long) {
        if (googleAnrTimestamps.size >= MAX_GOOGLE_ANR_COUNT) {
            logger.logWarning("The max number of Google ANR intervals has been reached. Ignoring this one.")
            return
        }
        googleAnrTimestamps.add(timestamp)
    }

    /**
     * Gets the intervals during which the application was not responding (ANR).
     *
     * @param startTime the time to search from
     * @param endTime the time to search until
     * @return the list of Google ANR timestamps
     */

    fun getGoogleAnrTimestamps(startTime: Long, endTime: Long): List<Long> {
        synchronized(this) {
            val extendedStartTime = startTime - TIME_MARGIN
            val extendedEndTime = endTime + TIME_MARGIN
            val results = mutableListOf<Long>()
            if (extendedStartTime > extendedEndTime) {
                return emptyList()
            }
            for (value in googleAnrTimestamps) {
                // Values were added to the end of the list, so once we have a value past
                // the end time, any other values would also be past the end time.
                if (value > extendedEndTime) {
                    break
                } else if (value >= extendedStartTime) {
                    results.add(value)
                }
            }
            return results
        }
    }

    fun clear() {
        googleAnrTimestamps.clear()
    }
}
