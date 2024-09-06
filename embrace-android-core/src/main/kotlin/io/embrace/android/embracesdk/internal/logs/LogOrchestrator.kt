package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler

interface LogOrchestrator : CrashTeardownHandler {

    /**
     * Flushes immediately any log still in the sink
     */
    fun flush(saveOnly: Boolean)
}

internal const val MAX_LOGS_PER_BATCH = 50
