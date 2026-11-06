package io.embrace.android.embracesdk.internal.perfetto

import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.io.IOException

/** The metadata of [path], following it if it is a symlink, or null when it (or its target) is not there. */
internal fun FileSystem.resolvedMetadata(path: Path): FileMetadata? {
    val metadata = metadataOrNull(path) ?: return null
    if (metadata.symlinkTarget == null) {
        return metadata
    }
    return try {
        metadataOrNull(canonicalize(path))
    } catch (exc: IOException) {
        null
    }
}

/** The size of [path] in bytes, or zero when it is not there. */
internal fun FileSystem.sizeOf(path: Path): Long = resolvedMetadata(path)?.size ?: 0L

/** [path] resolved against the working directory, for messages. */
internal fun absolute(path: Path): Path =
    if (path.isAbsolute) path else checkNotNull(System.getProperty("user.dir")).toPath() / path
