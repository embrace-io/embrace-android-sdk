package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.internal.arch.store.DeliveredAeiStore
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.storage.writeAtomically
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import okio.FileSystem
import okio.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [DeliveredAeiStore] backed by the file at [path], whose parent directory must exist. The file is read on first
 * access. A missing or unreadable file reads as no delivered IDs.
 *
 * Changes are written on [worker], and every change made before that write runs is written together. Passing the
 * worker that processes AEI records therefore gives one write per pass.
 *
 * If [initialIds] is given, the store starts from it instead of reading the file. Writes wait for [writes] to open.
 */
class FileDeliveredAeiStore(
    private val fileSystem: FileSystem,
    private val path: Path,
    private val worker: BackgroundWorker,
    private val errorHandler: ErrorHandler,
    initialIds: Set<String>? = null,
    private val writes: WriteBarrier = WriteBarrier(open = true),
) : DeliveredAeiStore {

    private val lock = Any()
    private val writePending = AtomicBoolean(false)
    private var current: Set<String>? = initialIds

    /**
     * The message last read, kept so fields written by a newer SDK survive a rewrite. Guarded by [lock].
     */
    private var base = DeliveredAeiProto(ids = initialIds.orEmpty().toList())

    override fun ids(): Set<String> = synchronized(lock) { loaded() }

    override fun add(id: String) = update { it.plus(id) }

    override fun retainOnly(ids: Set<String>) = update { it.intersect(ids) }

    private fun update(action: (Set<String>) -> Set<String>) {
        val changed = synchronized(lock) {
            val prev = loaded()
            val next = action(prev)
            current = next
            next != prev
        }
        if (changed && writePending.compareAndSet(false, true)) {
            writes.runWhenOpen { worker.submit(::write) }
        }
    }

    private fun loaded(): Set<String> = current ?: load().also { current = it }

    private fun load(): Set<String> {
        base = errorHandler.guardSdkCode(SdkErrorType.DeliveredAeiLoadFail, null) {
            if (fileSystem.exists(path)) fileSystem.read(path) { DeliveredAeiCodec.decode(this) } else null
        } ?: DeliveredAeiProto()
        return base.ids.toSet()
    }

    private fun write() {
        writePending.set(false)
        errorHandler.guardSdkCode(SdkErrorType.DeliveredAeiWriteFail) {
            writeFile(fileSystem, path, synchronized(lock) { base.copy(ids = loaded().toList()) })
        }
    }

    companion object {

        fun create(fileSystem: FileSystem, path: Path, ids: Set<String>) {
            writeFile(fileSystem, path, DeliveredAeiProto(ids = ids.toList()), sync = false)
        }

        private fun writeFile(fileSystem: FileSystem, path: Path, proto: DeliveredAeiProto, sync: Boolean = true) {
            fileSystem.writeAtomically(path, sync = sync) { DeliveredAeiCodec.encode(proto, it) }
        }
    }
}
