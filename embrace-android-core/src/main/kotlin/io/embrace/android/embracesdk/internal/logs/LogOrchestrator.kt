package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler

public interface LogOrchestrator : CrashTeardownHandler {

    /**
     * Flushes immediately any log still in the sink
     */
    public fun flush(saveOnly: Boolean)
    public fun onLogsAdded()
}

internal const val MAX_LOGS_PER_BATCH = 50
