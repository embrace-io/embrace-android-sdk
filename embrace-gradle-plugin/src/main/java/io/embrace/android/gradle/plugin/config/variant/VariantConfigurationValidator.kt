package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.system.Environment

private const val EMBRACE_FILE_CONFIG_DOCS_URL =
    "https://embrace.io/docs/android/integration/add-embrace-sdk/#add-the-config-file"
private const val API_TOKEN_LENGTH = 32
private const val APP_ID_LENGTH = 5
const val EMBRACE_API_TOKEN_ENV_KEY = "EMBRACE_API_TOKEN"

/**
 * It is in charge of performing validations on VariantConfiguration.
 */
internal object VariantConfigurationValidator {

    private val logger = Logger(VariantConfigurationValidator::class.java)

    enum class VariantConfigurationSourceType {
        CONFIG_FILE,
        EXTENSION
    }

    /**
     * It validates given VariantConfiguration.
     *
     * @return validated VariantConfiguration. Note that values may have been updated
     */
    fun validate(
        configuration: EmbraceVariantConfig,
        sourceType: VariantConfigurationSourceType,
        environment: Environment
    ): EmbraceVariantConfig {
        return validateApiToken(
            validateAppId(
                configuration,
                sourceType
            ),
            sourceType,
            environment
        )
    }

    /**
     * It validates appId for given VariantConfiguration.
     * AppId is now an optional value.
     *
     * @return validated VariantConfiguration. Note that values may have been updated
     */
    private fun validateAppId(
        configuration: EmbraceVariantConfig,
        sourceType: VariantConfigurationSourceType
    ): EmbraceVariantConfig {
        with(configuration) {
            if (appId != null && appId.length != APP_ID_LENGTH) {
                // app id incorrect length
                val msg = when (sourceType) {
                    VariantConfigurationSourceType.CONFIG_FILE ->
                        "app_id must contain exactly $APP_ID_LENGTH " +
                            "characters. You can also check our documentation to get more information about the " +
                            "configuration file at $EMBRACE_FILE_CONFIG_DOCS_URL"

                    VariantConfigurationSourceType.EXTENSION -> "appId must contain exactly $APP_ID_LENGTH characters."
                }
                throw IllegalArgumentException(msg)
            }
        }

        return configuration
    }

    /**
     * It validates apiToken for given VariantConfiguration.
     * ApiToken is now an optional value.
     *
     * @return validated VariantConfiguration. Note that values may have been updated
     */
    private fun validateApiToken(
        configuration: EmbraceVariantConfig,
        sourceType: VariantConfigurationSourceType,
        environment: Environment
    ): EmbraceVariantConfig {
        val envApiToken = environment.getVariable(EMBRACE_API_TOKEN_ENV_KEY)

        if (configuration.apiToken.isNullOrEmpty() && envApiToken.isNullOrEmpty()) {
            return configuration
        }

        var validatedConfiguration = configuration

        val errorMessage = if (!configuration.apiToken.isNullOrEmpty()) {
            // configuration apiToken not null
            if (configuration.apiToken.length != API_TOKEN_LENGTH) {
                // incorrect configuration apiToken length
                when (sourceType) {
                    VariantConfigurationSourceType.CONFIG_FILE ->
                        "api_token must contain exactly $API_TOKEN_LENGTH " +
                            "characters. You can also check our documentation to get more information about the " +
                            "configuration file at $EMBRACE_FILE_CONFIG_DOCS_URL"

                    VariantConfigurationSourceType.EXTENSION ->
                        "apiToken must contain exactly $API_TOKEN_LENGTH " +
                            "characters."
                }
            } else {
                // no error
                null
            }
        } else if (!envApiToken.isNullOrEmpty()) {
            if (envApiToken.length != API_TOKEN_LENGTH) {
                // incorrect configuration apiToken length
                when (sourceType) {
                    VariantConfigurationSourceType.CONFIG_FILE ->
                        "api_token must contain exactly $API_TOKEN_LENGTH " +
                            "characters. You can also check our documentation to get more information about the " +
                            "configuration file at $EMBRACE_FILE_CONFIG_DOCS_URL"

                    VariantConfigurationSourceType.EXTENSION ->
                        "apiToken must contain exactly $API_TOKEN_LENGTH " +
                            "characters."
                }
            } else {
                // configuration apiToken is null but environment apiToken is not null
                validatedConfiguration = configuration.copy(
                    appId = configuration.appId,
                    apiToken = envApiToken,
                    ndkEnabled = configuration.ndkEnabled,
                    sdkConfig = configuration.sdkConfig,
                    unityConfig = configuration.unityConfig
                )

                // no error
                null
            }
        } else {
            null // no error
        }

        if (!configuration.apiToken.isNullOrEmpty() && !envApiToken.isNullOrEmpty()) {
            logger.warn(
                "API tokens were found in both the environment variable and the configuration file. " +
                    "The latter (${validatedConfiguration.apiToken}) will be used."
            )
        }

        if (!errorMessage.isNullOrEmpty()) {
            throw IllegalArgumentException(errorMessage)
        }

        return validatedConfiguration
    }
}
