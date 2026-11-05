package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.storage.writeAtomically
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.MAPPED_BYTES
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.UNSET
import okio.FileHandle
import okio.FileSystem
import okio.IOException
import okio.Path
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * [OrdinalStore] backed by a memory-mapped file.
 *
 * mmap trade-offs:
 * - Writes are never fsynced. They survive process death, but power loss can drop recent increments.
 * - Truncating the file in place while it's mapped could make the next access throw native errors.
 */
class MmapOrdinalStore internal constructor(
    private val buffer: MappedByteBuffer,
    private val errorHandler: ErrorHandler,
) : OrdinalStore {

    private val lock = Any()

    override fun incrementAndGet(ordinal: Ordinal, scope: String?, seed: () -> Int): Int = synchronized(lock) {
        errorHandler.guardSdkCode(SdkErrorType.PayloadStorageFail, -1) {
            val offset = OrdinalFileFormat.slotOffset(ordinal)
            val current = buffer.getInt(offset)
            val next = if (current == UNSET) seed() else current + 1
            buffer.putInt(offset, next)
            next
        }
    }

    companion object {

        /**
         * Maps [file], padding it to the current slot count. Returns null if it's missing, or tracks
         * [SdkErrorType.OrdinalStoreOpenFail] and returns null if it's unusable.
         */
        fun open(file: File, errorHandler: ErrorHandler): MmapOrdinalStore? {
            if (!file.exists()) {
                return null
            }
            return errorHandler.guardSdkCode(SdkErrorType.OrdinalStoreOpenFail, null) {
                MmapOrdinalStore(map(file), errorHandler)
            }
        }

        /**
         * Atomically writes [path] with [initial] values; map it afterwards with [open].
         * - Don't call it while [path] is mapped: the rename orphans that mapping and its later increments are lost.
         * - [initial] must not contain [Ordinal.USER_SESSION], which shares [Ordinal.SESSION]'s slot.
         * - It rethrows anything [fileSystem] throws, so guard the call rather than catching only [IOException].
         */
        @Throws(IOException::class)
        fun create(
            fileSystem: FileSystem,
            path: Path,
            initial: Map<Ordinal, Int>,
        ) {
            fileSystem.writeAtomically(path) { sink ->
                OrdinalFileFormat.write(sink, initial)
            }
        }

        private fun map(file: File): MappedByteBuffer {
            val raf = RandomAccessFile(file, "rw")
            return RandomAccessFileHandle(raf).use { handle ->
                OrdinalFileFormat.prepare(handle)
                raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, MAPPED_BYTES.toLong())
            }
        }
    }
}

/**
 * okio view of the [RandomAccessFile] that is also mapped, so the file is only opened once.
 */
private class RandomAccessFileHandle(private val file: RandomAccessFile) : FileHandle(readWrite = true) {

    override fun protectedRead(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int): Int {
        file.seek(fileOffset)
        return file.read(array, arrayOffset, byteCount)
    }

    override fun protectedWrite(fileOffset: Long, array: ByteArray, arrayOffset: Int, byteCount: Int) {
        file.seek(fileOffset)
        file.write(array, arrayOffset, byteCount)
    }

    override fun protectedFlush() = file.fd.sync()

    override fun protectedResize(size: Long) = file.setLength(size)

    override fun protectedSize(): Long = file.length()

    override fun protectedClose() = file.close()
}
