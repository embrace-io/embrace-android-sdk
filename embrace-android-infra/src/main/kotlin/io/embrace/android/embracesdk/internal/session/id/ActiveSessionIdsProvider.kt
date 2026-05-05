package io.embrace.android.embracesdk.internal.session.id

interface ActiveSessionIdsProvider {
    /**
     * Returns the current user session and session part IDs atomically to be used for persistence on telemetry. Could be stale if a
     * session transition is happening concurrently, but the pair will always be consistent with each other.
     */
    fun getActiveSessionIds(): SessionIdsSnapshot
}
