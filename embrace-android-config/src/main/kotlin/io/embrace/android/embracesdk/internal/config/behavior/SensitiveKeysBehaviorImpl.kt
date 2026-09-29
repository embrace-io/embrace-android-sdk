package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.resolved.SensitiveKeysConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveSensitiveKeys

private const val SENSITIVE_KEY_MAX_LENGTH = 128
private const val SENSITIVE_KEYS_LIST_MAX_SIZE = 10000
const val REDACTED_LABEL: String = "<redacted>"

class SensitiveKeysBehaviorImpl(config: SensitiveKeysConfig) : SensitiveKeysBehavior {

    constructor(local: InstrumentedConfig) : this(resolveSensitiveKeys(behaviorInputs(local, null)))

    private val denyList = config.denylist?.take(SENSITIVE_KEYS_LIST_MAX_SIZE)

    override fun isSensitiveKey(key: String): Boolean {
        return denyList?.any { it.take(SENSITIVE_KEY_MAX_LENGTH) == key } ?: false
    }
}
