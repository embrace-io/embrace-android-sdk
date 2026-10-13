package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.clock.Clock
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

internal const val LAST_ALIVE_FILE_NAME = "last_alive"

/**
 * Keeps a session part's last alive time in a small memory-mapped file of its own in the part's directory,
 * rather than in file system metadata. The time comes from [clock], the clock the part's telemetry is
 * timestamped with.
 *
 * A record is a single store to mapped memory, which the kernel writes back even if the process dies right
 * after it. The file holds the time it was created, then the milliseconds since then as a 32-bit value written
 * twice in one 64-bit store. A reader only trusts the offset if both copies match, which rules out a torn
 * write. All of the file's bytes are written before it is mapped, so a store never has to allocate disk space
 * and cannot fail when the disk is full.
 */
class MappedFileLastAliveStore(private val clock: Clock) : SessionPartLastAliveStore {

    override fun recorder(partDir: File): SessionPartLastAliveRecorder {
        val file = LastAliveFile(File(partDir, LAST_ALIVE_FILE_NAME), clock)
        return SessionPartLastAliveRecorder { file.update() }
    }

    override fun lastAliveMs(partDir: File): Long? = try {
        DataInputStream(File(partDir, LAST_ALIVE_FILE_NAME).inputStream().buffered()).use { stream ->
            val createdMs = stream.readLong()
            val offset = stream.readInt()
            val check = stream.readInt()
            if (offset == check && offset >= 0) {
                createdMs + offset
            } else {
                null
            }
        }
    } catch (exc: IOException) {
        null
    }

    /**
     * The mapped file for one part. Creating it does I/O, so it happens on the thread that writes the part.
     */
    private class LastAliveFile(file: File, private val clock: Clock) {

        private val createdMs: Long = clock.now()
        private val mapped: MappedByteBuffer = RandomAccessFile(file, "rw").use { raf ->
            raf.write(ByteBuffer.allocate(FILE_BYTES).putLong(createdMs).putLong(0).array())
            raf.setLength(FILE_BYTES.toLong())
            raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, FILE_BYTES.toLong())
        }

        /**
         * Records the clock's time now. Offsets past what 32 bits hold are capped, which is far longer than a
         * session part lasts.
         */
        fun update() {
            val offset = (clock.now() - createdMs).coerceIn(0L, Int.MAX_VALUE.toLong())
            mapped.putLong(Long.SIZE_BYTES, offset or (offset shl Int.SIZE_BITS))
        }

        private companion object {
            const val FILE_BYTES = Long.SIZE_BYTES * 2
        }
    }
}
