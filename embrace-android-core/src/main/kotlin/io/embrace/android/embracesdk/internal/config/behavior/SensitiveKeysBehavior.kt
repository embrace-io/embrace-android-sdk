package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.RedactionConfig

interface SensitiveKeysBehavior : ConfigBehavior<RedactionConfig, UnimplementedConfig> {

    /**
     * Checks if the given key is sensitive.
     *
     * @param key The key to check.
     * @return true if the key is sensitive, otherwise false.
     */
    fun isSensitiveKey(key: String): Boolean
}
