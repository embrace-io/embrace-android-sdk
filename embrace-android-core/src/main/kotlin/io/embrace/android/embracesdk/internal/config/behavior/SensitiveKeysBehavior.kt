package io.embrace.android.embracesdk.internal.config.behavior

public fun interface SensitiveKeysBehavior {

    /**
     * Checks if the given key is sensitive.
     *
     * @param key The key to check.
     * @return true if the key is sensitive, otherwise false.
     */
    public fun isSensitiveKey(key: String): Boolean
}
