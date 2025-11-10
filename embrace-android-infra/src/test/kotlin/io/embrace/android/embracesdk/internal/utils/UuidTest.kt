package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Test

internal class UuidTest {

    @Test
    fun testUuid() {
        val uuid = Uuid.getEmbUuid("99fcae22-0db5-4b63-b49d-315eecce4889")
        assertEquals("99FCAE220DB54B63B49D315EECCE4889", uuid)
    }
}
