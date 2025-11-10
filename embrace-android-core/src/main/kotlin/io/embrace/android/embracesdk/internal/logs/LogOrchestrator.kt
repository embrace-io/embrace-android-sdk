package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.arch.CrashTeardownHandler

interface LogOrchestrator : CrashTeardownHandler {

    /**
     * Flushes immediately any log still in the sink
     */
    fun flush(saveOnly: Boolean)
    fun onLogsAdded()
}
