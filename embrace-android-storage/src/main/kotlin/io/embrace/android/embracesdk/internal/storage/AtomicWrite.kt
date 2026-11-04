package io.embrace.android.embracesdk.internal.storage

import okio.BufferedSink
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import kotlin.random.Random

const val ATOMIC_WRITE_TMP_SUFFIX: String = ".tmp"

/**
 * Replaces [target]'s contents via a temp file, fsync and rename. The parent directory must exist.
 * Writes go through [okio.FileHandle] because [FileSystem.sink] never fsyncs.
 * Each write creates a uniquely named temp file, so it never reuses one a dead process left behind.
 */
@Throws(IOException::class)
fun FileSystem.writeAtomically(
    target: Path,
    random: Random = Random.Default,
    block: (BufferedSink) -> Unit,
) {
    val parent = target.parent ?: throw IOException("No parent directory: $target")
    val tmp = parent / atomicWriteTmpName(target.name, random)
    try {
        openReadWrite(tmp, mustCreate = true, mustExist = false).use { handle ->
            handle.sink().buffer().use(block)
            handle.flush()
        }
        atomicMove(tmp, target)
    } catch (exc: Throwable) {
        runCatching { delete(tmp, mustExist = false) }
        throw exc
    }
}

private fun atomicWriteTmpName(name: String, random: Random): String =
    "$name.${random.nextLong().toULong().toString(16)}$ATOMIC_WRITE_TMP_SUFFIX"
