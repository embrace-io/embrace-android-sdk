package io.embrace.android.embracesdk.internal.store.file

import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.HEADER_BYTES
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.SLOT_COUNT
import io.embrace.android.embracesdk.internal.store.file.OrdinalFileFormat.UNSET
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdinalFileFormatTest {

    @Test
    fun `every ordinal has its own slot except user session`() {
        val slots = Ordinal.entries.filter { it != Ordinal.USER_SESSION }.map(OrdinalFileFormat::slotIndex)
        assertEquals(slots.size, slots.toSet().size)
        assertEquals(OrdinalFileFormat.slotIndex(Ordinal.SESSION), OrdinalFileFormat.slotIndex(Ordinal.USER_SESSION))

        val offsets = slots.map(OrdinalFileFormat::slotOffset)
        assertTrue(offsets.all { it >= HEADER_BYTES && it + Int.SIZE_BYTES <= OrdinalFileFormat.fileBytes(SLOT_COUNT) })
        assertArrayEquals("ORDS".toByteArray(), ordinalFile().copyOf(HEADER_BYTES))
    }

    @Test
    fun `zero bytes are unset and every value round trips`() {
        assertEquals(UNSET, OrdinalFileFormat.decode(0))
        assertEquals(0, OrdinalFileFormat.encode(UNSET))
        listOf(0, 1, 42, Int.MAX_VALUE, Int.MIN_VALUE).forEach {
            assertEquals(it, OrdinalFileFormat.decode(OrdinalFileFormat.encode(it)))
        }
    }
}
