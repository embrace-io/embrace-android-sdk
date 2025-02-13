package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.plugin.gradle.GradleCompatibilityHelper
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.gradle.api.provider.Provider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test

class VariantConfigBuilderTest {

    companion object {

        private val project = ProjectBuilder.builder().build()
        private val mockVariant: AndroidCompactedVariantData = mockk {
            every { name } returns "variantName"
        }

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkObject(GradleCompatibilityHelper)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkObject(GradleCompatibilityHelper)
        }
    }

    @Test
    fun `if variant configuration is not present it should not throw exception because it is optional`() {
        val builder = object : EmbraceVariantConfigurationBuilder() {
            override fun buildProvider(variant: AndroidCompactedVariantData): Provider<EmbraceVariantConfig> {
                return project.provider<EmbraceVariantConfig> {
                    null
                }
            }
        }

        assertNotNull(builder.buildVariantConfiguration(mockVariant))
    }

    @Test
    fun `build variant configuration successfully`() {
        val variantConfig = mockk<EmbraceVariantConfig>(relaxed = true)

        val builder = object : EmbraceVariantConfigurationBuilder() {
            override fun buildProvider(variant: AndroidCompactedVariantData): Provider<EmbraceVariantConfig> {
                return project.provider<EmbraceVariantConfig> {
                    variantConfig
                }
            }
        }

        val result = builder.buildVariantConfiguration(mockVariant)
        assertEquals(result.get(), variantConfig)
    }
}
