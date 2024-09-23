package io.embrace.android.embracesdk.internal.config.behavior

fun interface SensitiveKeysBehavior {

    /**
     * Checks if the given key is sensitive.
     *
     * @param key The key to check.
     * @return true if the key is sensitive, otherwise false.
     */
    fun isSensitiveKey(key: String): Boolean
}
