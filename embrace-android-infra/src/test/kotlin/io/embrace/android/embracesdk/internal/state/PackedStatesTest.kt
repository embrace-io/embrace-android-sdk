package io.embrace.android.embracesdk.internal.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PackedStatesTest {

    @Test
    fun `states are packed as name and value pairs, with a generated value marked`() {
        val packed = PackedStates.pack(
            listOf(
                PackedStates.Entry("screen-automatic", "CartActivity", false),
                PackedStates.Entry("network", "none", false),
                PackedStates.Entry("power", "unknown", true),
            ),
        )
        assertEquals("screen-automatic=CartActivity;network=none;power~unknown", packed)
    }

    @Test
    fun `a separator inside a value is escaped and read back`() {
        val entries = listOf(
            PackedStates.Entry("screen-automatic", "a;b=c~d\\e", false),
            PackedStates.Entry("network", "", true),
            PackedStates.Entry("power", "trailing\\", false),
            PackedStates.Entry("other", "multi\nline 😀", true),
        )
        val packed = PackedStates.pack(entries)
        assertEquals(
            "screen-automatic=a\\;b\\=c\\~d\\\\e;network~;power=trailing\\\\;other~multi\nline 😀",
            packed,
        )

        val unpacked = PackedStates.unpack(packed)
        assertEquals(entries.map(::describe), unpacked.map(::describe))
    }

    @Test
    fun `nothing packs to an empty string and unpacks to nothing`() {
        assertEquals("", PackedStates.pack(emptyList()))
        assertTrue(PackedStates.unpack("").isEmpty())
        assertTrue(PackedStates.unpack("no-assignment;;").isEmpty())
    }

    private fun describe(entry: PackedStates.Entry) = Triple(entry.name, entry.value, entry.system)
}
