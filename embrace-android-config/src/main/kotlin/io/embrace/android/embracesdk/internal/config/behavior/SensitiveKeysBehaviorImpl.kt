package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.RedactionConfig

private const val SENSITIVE_KEY_MAX_LENGTH = 128
private const val SENSITIVE_KEYS_LIST_MAX_SIZE = 10000

const val REDACTED_LABEL: String = "<redacted>"

class SensitiveKeysBehaviorImpl(
    local: InstrumentedConfig,
) : SensitiveKeysBehavior {

    override val local: RedactionConfig = local.redaction
    override val remote: UnimplementedConfig = null

    private val denyList = local.redaction.getSensitiveKeysDenylist()?.take(SENSITIVE_KEYS_LIST_MAX_SIZE)

    override fun isSensitiveKey(key: String): Boolean {
        return denyList?.any { it.take(SENSITIVE_KEY_MAX_LENGTH) == key } ?: false
    }
}
