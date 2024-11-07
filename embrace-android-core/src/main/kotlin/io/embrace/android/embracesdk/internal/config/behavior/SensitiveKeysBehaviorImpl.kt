package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig

private const val SENSITIVE_KEY_MAX_LENGTH = 128
private const val SENSITIVE_KEYS_LIST_MAX_SIZE = 10000

const val REDACTED_LABEL: String = "<redacted>"

class SensitiveKeysBehaviorImpl(
    denyList: List<String>? = null,
    instrumentedConfig: InstrumentedConfig
) : SensitiveKeysBehavior {

    private val denyList =
        (denyList ?: instrumentedConfig.redaction.getSensitiveKeysDenylist())?.take(SENSITIVE_KEYS_LIST_MAX_SIZE)

    override fun isSensitiveKey(key: String): Boolean {
        return denyList?.any { it.take(SENSITIVE_KEY_MAX_LENGTH) == key } ?: false
    }
}
