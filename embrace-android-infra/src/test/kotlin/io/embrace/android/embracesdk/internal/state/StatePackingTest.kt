package io.embrace.android.embracesdk.internal.state

import org.junit.Assert.assertEquals
import org.junit.Test

internal class StatePackingTest {

    @Test
    fun `packs observed and generated values and unpacks them back, escaping the separators`() {
        val snapshot = StateSnapshot.EMPTY
            .with(1, 0, "screen-automatic", "Initializing", isSystem = true)
            .with(2, 0, "network", "wifi", isSystem = false)
            .with(3, 0, "odd", "a;b=c~d\\e", isSystem = false)

        assertEquals("screen-automatic~Initializing;network=wifi;odd=a\\;b\\=c\\~d\\\\e", snapshot.packed)

        val unpacked = mutableListOf<Triple<String, String, Boolean>>()
        StatePacking.unpack(snapshot.packed) { name, value, isSystem -> unpacked.add(Triple(name, value, isSystem)) }
        assertEquals(
            listOf(
                Triple("screen-automatic", "Initializing", true),
                Triple("network", "wifi", false),
                Triple("odd", "a;b=c~d\\e", false),
            ),
            unpacked,
        )
    }

    @Test
    fun `replacing a value leaves the snapshot it came from as it was`() {
        val first = StateSnapshot.EMPTY.with(1, 10, "network", "wifi", isSystem = false)
        val second = first.with(2, 20, "network", "none", isSystem = false)
        assertEquals("network=wifi", first.packed)
        assertEquals("network=none", second.packed)
        assertEquals(2, second.seq)
    }
}
