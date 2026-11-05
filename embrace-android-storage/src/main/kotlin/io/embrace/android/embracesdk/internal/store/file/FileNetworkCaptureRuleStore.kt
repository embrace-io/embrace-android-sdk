package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.internal.arch.store.NetworkCaptureRuleStore
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.storage.writeAtomically
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import okio.FileSystem
import okio.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [NetworkCaptureRuleStore] backed by the file at [path], whose parent directory must exist. The file is read on
 * first access. A missing or unreadable file reads as no rule having been applied.
 *
 * The file records how many times each rule has been applied. This allows config changes to affect rules
 * that are already in use.
 *
 * Changes are written on [worker], and every change made before that write runs is written together. Each write
 * drops rules that aren't in [liveRuleIds], which should return the IDs of the current remote config's unexpired
 * rules.
 */
class FileNetworkCaptureRuleStore(
    private val fileSystem: FileSystem,
    private val path: Path,
    private val worker: BackgroundWorker,
    private val errorHandler: ErrorHandler,
    private val liveRuleIds: () -> Set<String>,
) : NetworkCaptureRuleStore {

    private val lock = Any()
    private val writePending = AtomicBoolean(false)
    private var current: Map<String, Int>? = null
    private var base = NetworkCaptureProto()

    override fun remaining(ruleId: String, default: Int): Int = synchronized(lock) {
        maxOf(default - (loaded()[ruleId] ?: 0), 0)
    }

    override fun decrement(ruleId: String, default: Int) {
        val changed = synchronized(lock) {
            val prev = loaded()
            val used = prev[ruleId] ?: 0
            if (used < default) {
                current = prev + (ruleId to used + 1)
                true
            } else {
                false
            }
        }
        if (changed && writePending.compareAndSet(false, true)) {
            worker.submit(::write)
        }
    }

    private fun loaded(): Map<String, Int> = current ?: load().also { current = it }

    private fun load(): Map<String, Int> {
        base = errorHandler.guardSdkCode(SdkErrorType.NetworkCaptureLoadFail, null) {
            if (fileSystem.exists(path)) fileSystem.read(path) { NetworkCaptureCodec.decode(this) } else null
        } ?: NetworkCaptureProto()
        return base.used
    }

    private fun write() {
        writePending.set(false)
        errorHandler.guardSdkCode(SdkErrorType.NetworkCaptureWriteFail) {
            val live = liveRuleIds()
            val proto = synchronized(lock) {
                val next = loaded().filterKeys(live::contains)
                current = next
                base.copy(used = next)
            }
            writeFile(fileSystem, path, proto)
        }
    }

    companion object {

        fun create(fileSystem: FileSystem, path: Path, used: Map<String, Int>) {
            writeFile(fileSystem, path, NetworkCaptureProto(used = used))
        }

        private fun writeFile(fileSystem: FileSystem, path: Path, proto: NetworkCaptureProto) {
            fileSystem.writeAtomically(path) { NetworkCaptureCodec.encode(proto, it) }
        }
    }
}
