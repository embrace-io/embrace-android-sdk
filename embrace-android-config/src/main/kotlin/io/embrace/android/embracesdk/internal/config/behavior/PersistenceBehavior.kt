package io.embrace.android.embracesdk.internal.config.behavior

interface PersistenceBehavior {

    /**
     * Whether the multi-file session persistence layer should write session telemetry
     * instead of the legacy single-file payload writer.
     */
    fun isMultiFilePersistenceEnabled(): Boolean
}
