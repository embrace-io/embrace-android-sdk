package io.embrace.android.embracesdk.internal.logs

public interface LogOrchestrator {

    /**
     * Flushes immediately any log still in the sink
     */
    public fun flush(saveOnly: Boolean)
}
internal const val MAX_LOGS_PER_BATCH = 50
