package io.embrace.android.embracesdk.fakes.config

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.RedactionConfig

class FakeRedactionConfig(
    base: RedactionConfig = InstrumentedConfigImpl.redaction,
    private val sensitiveKeys: List<String>? = base.getSensitiveKeysDenylist(),
    private val urlPatterns: List<String>? = base.getUrlRedactionPatterns(),
) : RedactionConfig {
    override fun getSensitiveKeysDenylist(): List<String>? = sensitiveKeys
    override fun getUrlRedactionPatterns(): List<String>? = urlPatterns
}
