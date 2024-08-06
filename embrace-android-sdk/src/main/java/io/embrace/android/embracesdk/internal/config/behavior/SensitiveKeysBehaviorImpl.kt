package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig

private const val SENSITIVE_KEY_MAX_LENGTH = 128
private const val SENSITIVE_KEYS_LIST_MAX_SIZE = 10000

internal class SensitiveKeysBehaviorImpl(
    localConfig: SdkLocalConfig
) : SensitiveKeysBehavior {

    private val denyList = localConfig.sensitiveKeysDenylist?.take(SENSITIVE_KEYS_LIST_MAX_SIZE)

    override fun isSensitiveKey(key: String): Boolean {
        return denyList?.any { it.take(SENSITIVE_KEY_MAX_LENGTH) == key } ?: false
    }
}
