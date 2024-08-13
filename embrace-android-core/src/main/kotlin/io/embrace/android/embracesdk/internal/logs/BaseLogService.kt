package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener

public interface BaseLogService : MemoryCleanerListener {

    /**
     * Gets the number of error logs that have been recorded.
     *
     * @return the error logs count
     */
    public fun getErrorLogsCount(): Int
}
