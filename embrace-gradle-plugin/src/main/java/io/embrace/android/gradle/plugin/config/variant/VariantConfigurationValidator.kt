package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig

private const val API_TOKEN_LENGTH = 32
private const val APP_ID_LENGTH = 5
const val EMBRACE_API_TOKEN_ENV_KEY = "EMBRACE_API_TOKEN"

/**
 * It is in charge of performing validations on VariantConfiguration.
 */
internal object VariantConfigurationValidator {

    /**
     * Validates given VariantConfiguration. Throws IllegalArgumentException if validation fails.
     */
    fun validate(configuration: EmbraceVariantConfig) {
        validateAppId(configuration)
        validateApiToken(configuration)
    }

    /**
     * Validates appId, if provided, for given VariantConfiguration.
     * Throws IllegalArgumentException if validation fails.
     */
    private fun validateAppId(configuration: EmbraceVariantConfig) {
        if (!configuration.appId.isNullOrEmpty()) {
            require(configuration.appId.length == APP_ID_LENGTH) {
                "app_id must contain exactly $APP_ID_LENGTH characters."
            }
        }
    }

    /**
     * It validates apiToken, if provided, for given VariantConfiguration.
     * Throws IllegalArgumentException if validation fails.
     */
    private fun validateApiToken(configuration: EmbraceVariantConfig) {
        if (!configuration.apiToken.isNullOrEmpty()) {
            require(configuration.apiToken.length == API_TOKEN_LENGTH) {
                "api_token must contain exactly $API_TOKEN_LENGTH characters."
            }
        }
    }
}
