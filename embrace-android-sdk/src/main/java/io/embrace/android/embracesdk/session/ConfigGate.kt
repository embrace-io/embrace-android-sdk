package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.config.ConfigListener
import io.embrace.android.embracesdk.config.ConfigService

/**
 * Gate that controls access to a service based on a config value. This
 * returns the service when it is enabled, or null if it is not.
 */
internal class ConfigGate<T>(
    private val impl: T,

    /**
     * Predicate that determines if the service should be enabled or not, via a config value.
     */
    private val predicate: () -> Boolean,
) : ConfigListener {

    private var configState: Boolean = predicate()

    /**
     * Returns the service when it is enabled, or null if it is not.
     */
    fun getService(): T? = when {
        configState -> impl
        else -> null
    }

    override fun onConfigChange(configService: ConfigService) {
        configState = predicate()
    }
}
