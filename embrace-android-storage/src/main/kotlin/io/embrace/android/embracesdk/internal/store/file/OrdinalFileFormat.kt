package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.internal.store.Ordinal

/**
 * Big-endian `ordinals.bin`, only ever written through a mapping: a 4-byte `ORDS` magic, then one int per slot.
 *
 * A slot holds its value plus one, so zero bytes read as [UNSET] and growing the file adds unset slots. Slot indices
 * are the format, so newer versions may only append slots, and a version leaves alone any slots beyond those it knows.
 */
internal object OrdinalFileFormat {
    const val MAGIC = 0x4F524453 // "ORDS"
    const val UNSET: Int = -1

    const val MAGIC_OFFSET: Int = 0
    const val HEADER_BYTES: Int = 4

    val SLOT_COUNT: Int = Ordinal.entries.maxOf(::slotIndex) + 1

    fun fileBytes(slots: Int): Long = HEADER_BYTES + slots.toLong() * Int.SIZE_BYTES

    fun slotOffset(slot: Int): Int = HEADER_BYTES + slot * Int.SIZE_BYTES

    fun encode(value: Int): Int = value + 1

    fun decode(raw: Int): Int = raw - 1

    /**
     * Slot indices are the file format. Never reorder or reuse; prefer appending new ones.
     */
    fun slotIndex(ordinal: Ordinal): Int = when (ordinal) {
        Ordinal.CRASH -> 0
        Ordinal.NATIVE_CRASH -> 1
        Ordinal.AEI_CRASH -> 2
        Ordinal.SESSION, Ordinal.USER_SESSION -> 3
        Ordinal.SESSION_PART -> 4
        Ordinal.APP_VERSION_STARTUP -> 5
    }
}
