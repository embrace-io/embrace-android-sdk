package io.embrace.android.embracesdk.config

/**
 * Notifies listeners on changes in the [ConfigService].
 */
internal fun interface ConfigListener {

    /**
     * Called when the [ConfigService] has been updated in some way. This typically means
     * that the remote config has been fetched from the server. This allows callers
     * to check config when this happens if they wish.
     */
    fun onConfigChange(configService: ConfigService)
}
