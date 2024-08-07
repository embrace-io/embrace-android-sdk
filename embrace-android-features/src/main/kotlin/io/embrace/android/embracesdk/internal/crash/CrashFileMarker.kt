package io.embrace.android.embracesdk.internal.crash

import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler

public interface CrashFileMarker : CrashTeardownHandler {

    /**
     * Creates a file in the cache directory to indicate that a crash has occurred.
     * If the file could not be created, it will try again.
     */
    public fun mark()

    /**
     * Deletes the file in the cache directory that indicates that a crash has occurred.
     * If the file could not be deleted, it will try again.
     */
    public fun removeMark()

    /**
     * Returns true if the crash marker file in the cache directory exists.
     * If the file could not be accessed, it will try again.
     */
    public fun isMarked(): Boolean

    /**
     * Returns true if the crash marker file in the cache directory exists and deletes it.
     */
    public fun getAndCleanMarker(): Boolean
}
