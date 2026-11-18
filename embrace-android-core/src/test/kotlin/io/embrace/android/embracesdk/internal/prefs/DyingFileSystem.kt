package io.embrace.android.embracesdk.internal.prefs

import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import java.util.concurrent.CountDownLatch

internal class DyingFileSystem(
    delegate: FileSystem,
    private val diesAt: Path,
    private val afterMove: Boolean,
) : ForwardingFileSystem(delegate) {

    val died = CountDownLatch(1)
    private val released = CountDownLatch(1)

    fun release() = released.countDown()

    override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        if (died.count == 0L) {
            die()
        }
        return path
    }

    override fun atomicMove(source: Path, target: Path) {
        if (target != diesAt) {
            super.atomicMove(source, target)
            return
        }
        if (afterMove) {
            super.atomicMove(source, target)
        }
        died.countDown()
        die()
    }

    private fun die(): Nothing {
        released.await()
        throw IOException("process died")
    }
}
