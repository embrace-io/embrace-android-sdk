package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.fakes.FakeEnvironment
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.system.Environment
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantConfigValidatorTest {

    @Test
    fun `do not fail if appId and apiToken are null because they are optional`() {
        val configuration = getVariantConfiguration()

        VariantConfigurationValidator.validate(
            configuration,
            VariantConfigurationValidator.VariantConfigurationSourceType.CONFIG_FILE,
            FakeEnvironment()
        )
    }

    @Test
    fun `fail if appId length is not valid`() {
        val configuration = getVariantConfiguration(
            appId = "appIdVeryLong"
        )

        assertValidationFailure(
            configuration,
            VariantConfigurationValidator.VariantConfigurationSourceType.CONFIG_FILE,
            "app_id must contain exactly"
        )

        assertValidationFailure(
            configuration,
            VariantConfigurationValidator.VariantConfigurationSourceType.EXTENSION,
            "appId must contain exactly"
        )
    }

    @Test
    fun `fail if apiToken length is not valid`() {
        val configuration = getVariantConfiguration(
            appId = "abcde",
            apiToken = "asdf",
        )

        assertValidationFailure(
            configuration,
            VariantConfigurationValidator.VariantConfigurationSourceType.CONFIG_FILE,
            "api_token must contain exactly",
            FakeEnvironment()
        )

        assertValidationFailure(
            configuration,
            VariantConfigurationValidator.VariantConfigurationSourceType.EXTENSION,
            expectedMessageInException = "apiToken must contain exactly",
            FakeEnvironment()
        )
    }

    @Test
    fun `fail if apiToken is not valid and env api token length is not valid`() {
        val configuration = getVariantConfiguration(
            appId = "abcde"
        )

        assertValidationFailure(
            configuration = configuration,
            sourceType = VariantConfigurationValidator.VariantConfigurationSourceType.CONFIG_FILE,
            expectedMessageInException = "api_token must contain exactly",
            environment = FakeEnvironment(mapOf(EMBRACE_API_TOKEN_ENV_KEY to "asdf"))
        )

        assertValidationFailure(
            configuration = configuration,
            sourceType = VariantConfigurationValidator.VariantConfigurationSourceType.EXTENSION,
            expectedMessageInException = "apiToken must contain exactly",
            environment = FakeEnvironment(mapOf(EMBRACE_API_TOKEN_ENV_KEY to "asdf"))
        )
    }

    private fun getVariantConfiguration(
        appId: String? = null,
        apiToken: String? = null,
        ndkEnabled: Boolean? = null,
    ) = EmbraceVariantConfig(
        appId,
        apiToken,
        ndkEnabled,
        null,
        null
    )

    private fun assertValidationFailure(
        configuration: EmbraceVariantConfig,
        sourceType: VariantConfigurationValidator.VariantConfigurationSourceType,
        expectedMessageInException: String,
        environment: Environment = FakeEnvironment()
    ) {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            VariantConfigurationValidator.validate(
                configuration = configuration,
                sourceType = sourceType,
                environment = environment
            )
        }

        assertTrue(exception.message?.contains(expectedMessageInException) ?: false)
    }
}
