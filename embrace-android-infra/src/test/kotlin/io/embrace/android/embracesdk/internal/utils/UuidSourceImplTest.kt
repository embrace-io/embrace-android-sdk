package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

internal class UuidSourceImplTest {

    @Test
    fun testUuid() {
        val uuid = UuidSourceImpl(Random(0)).createUuid()
        assertEquals("8CB4C22C53FEAE50D94E97B2A94E6B1E", uuid)
    }
}
