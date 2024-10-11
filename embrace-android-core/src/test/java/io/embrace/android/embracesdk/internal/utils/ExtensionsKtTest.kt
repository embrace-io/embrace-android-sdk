package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ExtensionsKtTest {

    @Test
    fun `blankish values return true when isBlankish invoked`() {
        val blankishValues = listOf("", " ", "null", "NULL")

        blankishValues.forEach { value ->
            assertTrue(value.isBlankish())
        }

        assertFalse("test".isBlankish())
    }
}
