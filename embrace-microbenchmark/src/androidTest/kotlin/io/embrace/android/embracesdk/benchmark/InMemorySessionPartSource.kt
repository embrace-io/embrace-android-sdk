package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.session.persistence.SessionPartFile
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartSource
import okio.Buffer
import okio.BufferedSource

/**
 * Serves session part files that were never written to a filesystem.
 */
internal class InMemorySessionPartSource(
    private val files: Map<SessionPartFile, ByteArray>,
) : SessionPartSource {

    override fun exists(file: SessionPartFile): Boolean = file in files

    override fun open(file: SessionPartFile): BufferedSource? = files[file]?.let { Buffer().write(it) }

    override fun sizeBytes(file: SessionPartFile): Long = files[file]?.size?.toLong() ?: 0L
}
