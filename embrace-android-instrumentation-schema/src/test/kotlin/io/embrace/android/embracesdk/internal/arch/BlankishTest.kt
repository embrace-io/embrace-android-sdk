package io.embrace.android.embracesdk.internal.arch

import org.junit.Assert
import org.junit.Test

internal class BlankishTest {

    @Test
    fun `blankish values return true when isBlankish invoked`() {
        val blankishValues = listOf("", " ", "null", "NULL")

        blankishValues.forEach { value ->
            Assert.assertTrue(value.isBlankish())
        }

        Assert.assertFalse("test".isBlankish())
    }
}
