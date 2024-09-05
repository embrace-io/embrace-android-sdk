package io.embrace.android.embracesdk.internal.config.instrumented

/**
 * Declares how the SDK should redact sensitive data
 */
@Suppress("FunctionOnlyReturningConstant")
@Swazzled
object RedactionConfig {

    /**
     * Provides a list of sensitive keys whose values should be redacted on capture.
     *
     * sdk_config.sensitive_keys_denylist
     */
    fun getSensitiveKeysDenylist(): List<String>? = null
}
