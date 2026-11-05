package io.embrace.android.embracesdk.internal.store.file

import okio.Buffer

internal const val ORDS_MAGIC = 0x4F524453

/**
 * Hand-built `ordinals.bin` bytes, independent of [OrdinalFileFormat.write].
 */
internal fun ordinalFile(
    vararg slots: Int,
    version: Int = 1,
    magic: Int = ORDS_MAGIC,
): ByteArray = Buffer().apply {
    writeInt(magic)
    writeInt(version)
    slots.forEach(::writeInt)
}.readByteArray()
