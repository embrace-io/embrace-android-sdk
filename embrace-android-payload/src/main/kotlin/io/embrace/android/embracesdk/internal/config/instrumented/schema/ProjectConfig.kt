package io.embrace.android.embracesdk.internal.config.instrumented.schema

/**
 * Declares metadata about the app project
 */
interface ProjectConfig {

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
     * The project's buildId
     *
     * This is not possible to specify in the embrace-config.json.
     */
    fun getBuildId(): String? = null

    /**
     * The project's buildType
     *
     * This is not possible to specify in the embrace-config.json.
     */
    fun getBuildType(): String? = null

    /**
     * The project's buildFlavor
     *
     * This is not possible to specify in the embrace-config.json.
     */
    fun getBuildFlavor(): String? = null
}
