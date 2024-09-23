package io.embrace.android.embracesdk.internal.config.instrumented

/**
 * Declares the base URLs the SDK should use in HTTP requests
 */
@Suppress("FunctionOnlyReturningConstant")
@Swazzled
object BaseUrlConfig {

    /**
     * Config base URL
     *
     * sdk_config.base_urls.config
     */
    fun getConfig(): String? = null

    /**
     * Data base URL
     *
     * sdk_config.base_urls.data
     */
    fun getData(): String? = null
}
