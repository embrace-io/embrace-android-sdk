package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantConfigValidatorTest {

    @Test
    fun `do not fail if appId and apiToken are null because they are optional`() {
        val configuration = getVariantConfiguration()
        VariantConfigurationValidator.validate(configuration)
    }

    @Test
    fun `fail if appId length is not valid`() {
        val configuration = getVariantConfiguration(appId = "appIdVeryLong")
        assertValidationFailure(configuration, "app_id must contain exactly")
    }

    @Test
    fun `fail if apiToken length is not valid`() {
        val configuration = getVariantConfiguration(appId = "abcde", apiToken = "asdf")
        assertValidationFailure(configuration, "api_token must contain exactly")
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
        expectedMessageInException: String,
    ) {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            VariantConfigurationValidator.validate(configuration)
        }

        assertTrue(exception.message?.contains(expectedMessageInException) ?: false)
    }
}
