package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.gradle.testfixtures.ProjectBuilder
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test

class VariantConfigBuilderForValueSourceTest {

    companion object {
        private val project = ProjectBuilder.builder().build()
        private val variant = mockk<AndroidCompactedVariantData>(relaxed = true) {
            every { productFlavors } returns emptyList()
        }
        private val projectDirectory = project.layout.projectDirectory
        private val variantConfigurationBuilder = EmbraceVariantConfigurationBuilderForValueSource(
            projectDirectory,
            project.providers
        )
        private val expectedVariantConfig = mockk<EmbraceVariantConfig>(relaxed = true)

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkObject(EmbraceVariantConfigurationFileBuildStrategy)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkObject(EmbraceVariantConfigurationFileBuildStrategy)
        }
    }

    @Test
    fun `variant configuration could not be built, it should not throw exception because it is optional`() {
        every { EmbraceVariantConfigurationFileBuildStrategy.build(any(), projectDirectory, any()) } returns null

        assertNotNull(variantConfigurationBuilder.buildVariantConfiguration(variant))
    }

    @Test
    fun `variant configuration built from file`() {
        every { EmbraceVariantConfigurationFileBuildStrategy.build(any(), projectDirectory, any()) } returns
            expectedVariantConfig

        val variantConfigurationProvider = variantConfigurationBuilder.buildVariantConfiguration(
            variant
        )

        verify { EmbraceVariantConfigurationFileBuildStrategy.build(any(), projectDirectory, any()) }
        assertEquals(expectedVariantConfig, variantConfigurationProvider.get())
    }
}
