package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.MAGIC
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.MAGIC_OFFSET
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.SLOT_COUNT
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.UNSET
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.decode
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.encode
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.fileBytes
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.slotIndex
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.slotOffset
import okio.FileSystem
import okio.IOException
import okio.Path
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * [OrdinalStore] backed by a memory-mapped file that is created, grown and written in place, only ever through the
 * mapping. See [OrdinalFileFormat] for the layout.
 *
 * mmap trade-offs:
 * - Nothing is fsynced. Writes survive process death, but a power loss can drop recent changes.
 * - The file is only ever grown: truncating it while it's mapped could make the next access throw native errors.
 */
class MmapOrdinalStore private constructor(
    private val buffer: MappedByteBuffer,
    private val errorHandler: ErrorHandler,
) : OrdinalStore {

    /*
     * Changing an ordinal is a single aligned write to its slot. Zero bytes read as unset, so a new file, or the slots
     * this version adds to an older one, need no writes. Slots beyond those this version knows aren't mapped, so a newer
     * versions are left alone.
     */

    private val lock = Any()

    override fun incrementAndGet(ordinal: Ordinal, scope: String?, seed: () -> Int): Int = synchronized(lock) {
        errorHandler.guardSdkCode(SdkErrorType.PayloadStorageFail, -1) {
            val offset = slotOffset(slotIndex(ordinal))
            val value = decode(buffer.getInt(offset))
            val next = if (value == UNSET) seed() else value + 1
            buffer.putInt(offset, encode(next))
            next
        }
    }

    /**
     * Overwrites [ordinal]'s slot with [value].
     */
    internal fun reset(ordinal: Ordinal, value: Int) {
        synchronized(lock) {
            buffer.putInt(slotOffset(slotIndex(ordinal)), encode(value))
        }
    }

    companion object {

        /**
         * Maps [file], growing it to hold every slot this version knows. Returns null if it's missing, or tracks
         * [SdkErrorType.OrdinalStoreOpenFail] and returns null if it's unusable.
         *
         * Slots a shorter file didn't hold open unset. A bad magic, tracked and the file's slots are reset in place, so ordinals restart
         * at their seed instead of being unavailable for the life of the install. The file still exists afterwards, so the prefs migration
         * isn't re-run.
         */
        fun open(file: File, errorHandler: ErrorHandler): MmapOrdinalStore? {
            if (!file.exists()) {
                return null
            }
            return errorHandler.guardSdkCode(SdkErrorType.OrdinalStoreOpenFail, null) {
                val buffer = map(file) { errorHandler.trackSdkError(SdkErrorType.OrdinalStoreOpenFail, it) }
                MmapOrdinalStore(buffer, errorHandler)
            }
        }

        /**
         * Writes [path] in place with [initial] values for the slots this version knows, leaving any others alone; map
         * it afterwards with [open]. [path] is created through [fileSystem]. Nothing is fsynced, and it isn't atomic:
         * a process death part way through can leave some of [initial] written. [stage] and [commitStaged] write
         * values atomically.
         * - Don't call it while [path] is mapped: increments through that mapping race the writes.
         * - [initial] must not contain [Ordinal.USER_SESSION], which shares [Ordinal.SESSION]'s slot.
         * - It rethrows anything [fileSystem] throws, so guard the call rather than catching only [IOException].
         */
        @Throws(IOException::class)
        fun create(
            fileSystem: FileSystem,
            path: Path,
            initial: Map<Ordinal, Int>,
        ) {
            checkInitial(initial)
            fileSystem.openReadWrite(path, mustCreate = false, mustExist = false).close()
            write(map(path.toFile()) {}, initial)
        }

        /**
         * Writes [staged] with [initial] values, replacing any file already there, and maps it. Nothing is fsynced
         * until [commitStaged].
         */
        @Throws(IOException::class)
        fun stage(
            fileSystem: FileSystem,
            staged: Path,
            initial: Map<Ordinal, Int>,
            errorHandler: ErrorHandler,
        ): MmapOrdinalStore {
            checkInitial(initial)
            fileSystem.delete(staged, mustExist = false)
            fileSystem.openReadWrite(staged, mustCreate = true, mustExist = false).close()
            val buffer = map(staged.toFile()) {}
            write(buffer, initial)
            return MmapOrdinalStore(buffer, errorHandler)
        }

        /**
         * Fsyncs [staged] and renames it to [path]. A mapping of [staged] then maps [path], as it is the same file.
         */
        @Throws(IOException::class)
        fun commitStaged(fileSystem: FileSystem, staged: Path, path: Path) {
            fileSystem.openReadWrite(staged, mustCreate = false, mustExist = true).use { it.flush() }
            fileSystem.atomicMove(staged, path)
        }

        private fun checkInitial(initial: Map<Ordinal, Int>) {
            require(Ordinal.USER_SESSION !in initial) { "USER_SESSION shares SESSION's slot" }
        }

        /**
         * Maps the slots this version knows in [file], growing it if it holds fewer. Without the magic, its slots are
         * reset before the magic is written, and a magic other than zero is first passed to [onCorrupt].
         */
        private inline fun map(
            file: File,
            onCorrupt: (CorruptOrdinalFileException) -> Unit,
        ): MappedByteBuffer = RandomAccessFile(file, "rw").use { raf ->
            val bytes = fileBytes(SLOT_COUNT)
            if (raf.length() < bytes) {
                raf.setLength(bytes)
            }
            val buffer = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, bytes)
            val magic = buffer.getInt(MAGIC_OFFSET)
            if (magic != MAGIC) {
                if (magic != 0) {
                    onCorrupt(CorruptOrdinalFileException("Bad magic: $magic"))
                }
                for (slot in 0 until SLOT_COUNT) {
                    buffer.putInt(slotOffset(slot), 0)
                }
                storeStoreFence()
                buffer.putInt(MAGIC_OFFSET, MAGIC)
            }
            buffer
        }

        private fun write(buffer: MappedByteBuffer, initial: Map<Ordinal, Int>) {
            val values = IntArray(SLOT_COUNT) { UNSET }
            initial.forEach { (ordinal, value) -> values[slotIndex(ordinal)] = value }
            values.forEachIndexed { slot, value -> buffer.putInt(slotOffset(slot), encode(value)) }
        }

        @Volatile
        private var fence = 0

        /**
         * Keeps the stores before it ahead of those after it, which plain [MappedByteBuffer] stores aren't.
         */
        private fun storeStoreFence(): Int {
            // VarHandle's fences would be better, but need API 33.
            fence = 0
            return fence
        }
    }
}
