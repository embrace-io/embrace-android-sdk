package io.embrace.android.embracesdk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ListExtensionsKtTest {

    @Test
    fun testSafeGet() {
        val list = listOf("a", "b", "c")
        assertEquals("a", list.at(0))
        assertEquals("c", list.at(2))
        assertNull(list.at(-1))
        assertNull(list.at(100))
    }
}
