package io.embrace.android.embracesdk.fakes.config

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.RedactionConfig

class FakeRedactionConfig(
    base: RedactionConfig = InstrumentedConfigImpl.redaction,
    private val sensitiveKeys: List<String>? = base.getSensitiveKeysDenylist(),
) : RedactionConfig {
    override fun getSensitiveKeysDenylist(): List<String>? = sensitiveKeys
}
