package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.session.MemoryCleanerListener

internal interface BaseLogService : MemoryCleanerListener {
    /**
     * Finds all IDs of log events at error level within the given time window.
     *
     * @param startTime the beginning of the time window
     * @param endTime   the end of the time window
     * @return the list of log IDs within the specified range
     */
    fun findErrorLogIds(startTime: Long, endTime: Long): List<String>
}
