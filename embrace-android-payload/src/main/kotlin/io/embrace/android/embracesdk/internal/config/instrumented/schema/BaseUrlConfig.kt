package io.embrace.android.embracesdk.internal.config.instrumented.schema

/**
 * Declares the base URLs the SDK should use in HTTP requests
 */
interface BaseUrlConfig {

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
