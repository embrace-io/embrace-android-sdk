package io.embrace.android.embracesdk.internal.storage

import okio.BufferedSink
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer

const val ATOMIC_WRITE_TMP_SUFFIX: String = ".tmp"

/**
 * Replaces [target]'s contents via a temp file, fsync and rename. The parent directory must exist.
 * Writes go through [okio.FileHandle] because [FileSystem.sink] never fsyncs.
 */
@Throws(IOException::class)
fun FileSystem.writeAtomically(target: Path, block: (BufferedSink) -> Unit) {
    val parent = target.parent ?: throw IOException("No parent directory: $target")
    val tmp = parent / "${target.name}$ATOMIC_WRITE_TMP_SUFFIX"
    try {
        openReadWrite(tmp, mustCreate = false, mustExist = false).use { handle ->
            handle.resize(0)
            handle.sink().buffer().use(block)
            handle.flush()
        }
        atomicMove(tmp, target)
    } catch (exc: Throwable) {
        runCatching { delete(tmp, mustExist = false) }
        throw exc
    }
}
