package io.embrace.android.embracesdk.internal.config.instrumented

/**
 * Declares metadata about the app project
 */
@Suppress("FunctionOnlyReturningConstant")
@Swazzled
object ProjectConfig {

    /**
     * The project's appId
     *
     * app_id
     */
    fun getAppId(): String? = null

    /**
     * The project's app framework
     *
     * sdk_config.app_framework
     */
    fun getAppFramework(): String? = null

    /**
     * The ID of the particular build, generated at compile-time
     */
    fun getBuildId(): String = ""
}
