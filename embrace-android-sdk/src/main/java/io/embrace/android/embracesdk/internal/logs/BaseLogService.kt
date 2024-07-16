package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener

internal interface BaseLogService : MemoryCleanerListener {
    /**
     * Finds all IDs of log events at error level within the given time window.
     *
     * @return the list of log IDs within the specified range
     */
    fun findErrorLogIds(): List<String>
}
