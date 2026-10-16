package io.embrace.android.embracesdk.internal.logs

interface LogOrchestrator {

    /**
     * Flushes immediately any log still in the sink
     */
    fun flush(saveOnly: Boolean)
    fun onLogsAdded()

    /**
     * Saves any log still in the sink after the JVM crash identified by [crashId].
     */
    fun handleCrash(crashId: String)
}
