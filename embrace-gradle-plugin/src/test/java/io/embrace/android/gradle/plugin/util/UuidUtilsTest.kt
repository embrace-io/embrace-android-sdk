package io.embrace.android.gradle.plugin.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class UuidUtilsTest {

    @Test
    fun generateEmbraceUuid() {
        val uuid = UUID.fromString("20bd98bc-af93-4b17-98e1-baf14a635bf4")
        val output = UuidUtils.generateEmbraceUuid(uuid)
        assertEquals("20BD98BCAF934B1798E1BAF14A635BF4", output)
    }
}
