package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.resolved.SensitiveKeysConfig

private const val SENSITIVE_KEY_MAX_LENGTH = 128
private const val SENSITIVE_KEYS_LIST_MAX_SIZE = 10000

const val REDACTED_LABEL: String = "<redacted>"

/**
 * Decides whether keys are sensitive and should be redacted, based on the resolved [SensitiveKeysConfig].
 */
class SensitiveKeysBehavior(
    config: SensitiveKeysConfig,
) {

    private val denyList = config.denylist?.take(SENSITIVE_KEYS_LIST_MAX_SIZE)

    /**
     * Checks if the given key is sensitive.
     *
     * @param key The key to check.
     * @return true if the key is sensitive, otherwise false.
     */
    fun isSensitiveKey(key: String): Boolean {
        return denyList?.any { it.take(SENSITIVE_KEY_MAX_LENGTH) == key } ?: false
    }
}
