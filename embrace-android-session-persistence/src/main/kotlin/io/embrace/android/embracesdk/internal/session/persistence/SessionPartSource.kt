package io.embrace.android.embracesdk.internal.session.persistence

import okio.BufferedSource

/**
 * Supplies the bytes persisted for one session part, without prescribing where they are held.
 */
interface SessionPartSource {

    /**
     * Whether [file] exists or not
     */
    fun exists(file: SessionPartFile): Boolean

    /**
     * Opens [file] for reading, or returns null if there's nothing readable.
     */
    fun open(file: SessionPartFile): BufferedSource?

    /**
     * The number of bytes held for [file], or 0 if it was never written.
     */
    fun sizeBytes(file: SessionPartFile): Long
}
