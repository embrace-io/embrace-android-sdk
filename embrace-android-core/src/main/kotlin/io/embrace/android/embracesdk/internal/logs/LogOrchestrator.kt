package io.embrace.android.embracesdk.internal.logs

interface LogOrchestrator {

    /**
     * Flushes immediately any log still in the sink
     */
    fun flush(saveOnly: Boolean)
    fun onLogsAdded()
}
