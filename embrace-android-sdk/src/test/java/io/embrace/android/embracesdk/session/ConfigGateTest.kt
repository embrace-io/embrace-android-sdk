package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.fakes.FakeConfigService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ConfigGateTest {

    @Test
    fun `test config gate`() {
        var value = true
        val gate = ConfigGate("test") { value }
        assertEquals("test", gate.getService())

        value = false
        gate.onConfigChange(FakeConfigService())
        assertNull(gate.getService())
    }
}
