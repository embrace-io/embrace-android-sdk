package io.embrace.android.embracesdk.internal.store.file

import okio.Buffer

internal const val ORDS_MAGIC = 0x4F524453

/**
 * Hand-built `ordinals.bin` bytes, independent of [OrdinalFileFormat]: each slot holds its value plus one.
 */
internal fun ordinalFile(
    vararg slots: Int,
    magic: Int = ORDS_MAGIC,
): ByteArray = Buffer().apply {
    writeInt(magic)
    slots.forEach { writeInt(it + 1) }
}.readByteArray()
