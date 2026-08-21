package io.embrace.android.embracesdk.internal.config.behavior

interface PersistenceBehavior {

    /**
     * Whether the multi-file session persistence layer should write session telemetry in parallel
     * with the existing single-file payload writer.
     */
    fun isMultiFilePersistenceEnabled(): Boolean
}
