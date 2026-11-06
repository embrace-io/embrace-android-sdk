package io.embrace.android.embracesdk.internal.storage

import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path

internal class BlockingFileSystem(delegate: FileSystem, var blocked: Path?) : ForwardingFileSystem(delegate) {
    override fun atomicMove(source: Path, target: Path) {
        if (source == blocked) throw IOException("rename failed")
        super.atomicMove(source, target)
    }
}
