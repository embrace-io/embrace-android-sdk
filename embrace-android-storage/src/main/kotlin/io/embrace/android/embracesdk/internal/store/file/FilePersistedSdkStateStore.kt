package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.storage.writeAtomically
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateEditor
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.edit
import io.embrace.android.embracesdk.internal.utils.SystemTrace
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import okio.FileSystem
import okio.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [PersistedSdkStateStore] backed by the file at [path], whose parent directory must exist. The file is read on
 * first access. Updates made before the next background write are written together. A missing or unreadable file
 * reads as empty state with a new device ID and install timestamp.
 */
class FilePersistedSdkStateStore(
    private val fileSystem: FileSystem,
    private val path: Path,
    private val worker: BackgroundWorker,
    private val errorHandler: ErrorHandler,
    private val clock: Clock,
    private val uuidSource: UuidSource,
) : PersistedSdkStateStore {

    private val lock = Any()
    private val writePending = AtomicBoolean(false)

    @Volatile
    private var current: PersistedSdkState? = null
    private var base = PersistedSdkStateProto()

    override fun snapshot(): PersistedSdkState = current ?: synchronized(lock) {
        current ?: load().also {
            current = it
            // scheduled once the state is set, as a worker that runs inline would otherwise load it again
            if (base.device_id == null || base.install_timestamp_ms == null) {
                scheduleWrite()
            }
        }
    }

    override fun update(action: PersistedSdkStateEditor.() -> Unit): PersistedSdkState =
        synchronized(lock) { snapshot().edit(action).also { current = it } }.also { scheduleWrite() }

    private fun load(): PersistedSdkState = SystemTrace.trace("persisted-sdk-state-load") {
        base = errorHandler.guardSdkCode(SdkErrorType.PersistedSdkStateLoadFail, null) {
            if (fileSystem.exists(path)) fileSystem.read(path) { PersistedSdkStateCodec.decode(this) } else null
        } ?: PersistedSdkStateProto()
        base.toPersistedSdkState(base.device_id ?: uuidSource.createUuid(), base.install_timestamp_ms ?: clock.now())
    }

    private fun scheduleWrite() {
        if (writePending.compareAndSet(false, true)) {
            worker.submit(::write)
        }
    }

    private fun write() {
        writePending.lazySet(false)
        errorHandler.guardSdkCode(SdkErrorType.PersistedSdkStateWriteFail) {
            writeFile(fileSystem, path, synchronized(lock) { snapshot().toProto(base) })
        }
    }

    companion object {

        fun create(fileSystem: FileSystem, path: Path, state: PersistedSdkState) {
            writeFile(fileSystem, path, state.toProto(PersistedSdkStateProto()))
        }

        private fun writeFile(fileSystem: FileSystem, path: Path, proto: PersistedSdkStateProto) {
            fileSystem.writeAtomically(path) { PersistedSdkStateCodec.encode(proto, it) }
        }
    }
}
