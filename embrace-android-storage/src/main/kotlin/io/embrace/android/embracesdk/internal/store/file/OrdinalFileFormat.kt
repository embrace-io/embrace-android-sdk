package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.internal.store.Ordinal
import okio.BufferedSink
import okio.FileHandle
import okio.IOException
import okio.buffer

/**
 * Big-endian `ordinals.bin`: `ORDS` magic, version, then one int per slot, [UNSET] if unset.
 * Newer versions may only append slots.
 */
internal object OrdinalFileFormat {
    private const val MAGIC = 0x4F524453 // "ORDS"
    private const val VERSION = 1
    private const val HEADER_BYTES = 8
    const val UNSET = -1

    private val SLOT_COUNT = Ordinal.entries.maxOf(::slotIndex) + 1
    val MAPPED_BYTES = HEADER_BYTES + SLOT_COUNT * Int.SIZE_BYTES

    fun slotOffset(ordinal: Ordinal): Int = HEADER_BYTES + slotIndex(ordinal) * Int.SIZE_BYTES

    fun write(sink: BufferedSink, initial: Map<Ordinal, Int>) {
        require(Ordinal.USER_SESSION !in initial) { "USER_SESSION shares SESSION's slot" }
        val slots = IntArray(SLOT_COUNT) { UNSET }
        initial.forEach { (ordinal, value) -> slots[slotIndex(ordinal)] = value }
        sink.writeInt(MAGIC)
        sink.writeInt(VERSION)
        slots.forEach(sink::writeInt)
    }

    /**
     * Validates the header and pads missing or partial slots with [UNSET] so [MAPPED_BYTES] can be mapped. Never
     * truncates. Throws [CorruptOrdinalFileException] if the header is unusable.
     */
    @Throws(IOException::class)
    fun prepare(handle: FileHandle) {
        val size = handle.size()
        val problem = if (size < HEADER_BYTES) {
            "Truncated header: $size bytes"
        } else {
            handle.source().buffer().use { header ->
                val magic = header.readInt()
                val version = header.readInt()
                when {
                    magic != MAGIC -> "Bad magic: $magic"
                    version < VERSION -> "Unsupported version: $version"
                    else -> null
                }
            }
        }
        if (problem != null) {
            throw CorruptOrdinalFileException(problem)
        }
        val knownSlots = ((size - HEADER_BYTES) / Int.SIZE_BYTES).toInt()
        if (knownSlots < SLOT_COUNT) {
            handle.sink((HEADER_BYTES + knownSlots * Int.SIZE_BYTES).toLong()).buffer().use { sink ->
                repeat(SLOT_COUNT - knownSlots) { sink.writeInt(UNSET) }
            }
        }
    }

    /**
     * Slot indices are the file format. Never reorder or reuse; prefer appending new ones.
     */
    private fun slotIndex(ordinal: Ordinal): Int = when (ordinal) {
        Ordinal.CRASH -> 0
        Ordinal.NATIVE_CRASH -> 1
        Ordinal.AEI_CRASH -> 2
        Ordinal.SESSION, Ordinal.USER_SESSION -> 3
        Ordinal.SESSION_PART -> 4
        Ordinal.APP_VERSION_STARTUP -> 5
    }
}
