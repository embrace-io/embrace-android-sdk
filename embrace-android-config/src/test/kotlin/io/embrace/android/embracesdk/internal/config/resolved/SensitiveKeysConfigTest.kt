package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeRedactionConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class SensitiveKeysConfigTest {

    @Test
    fun `defaults match resolved default config`() {
        val defaults = EmbraceConfig().sensitiveKeys
        val resolved = resolveConfig(InstrumentedConfigImpl, null, unreadBucket).sensitiveKeys
        assertNull(defaults.denylist)
        assertEquals(defaults.denylist, resolved.denylist)
    }

    @Test
    fun `local denylist is used`() {
        val keys = listOf("password", "passkey")
        val local = FakeInstrumentedConfig(redaction = FakeRedactionConfig(sensitiveKeys = keys))
        assertEquals(keys, resolveSensitiveKeys(local).denylist)
    }
}
