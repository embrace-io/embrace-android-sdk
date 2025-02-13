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
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class VariantConfigFileBuildStrategyTest {

    companion object {
        private val project = ProjectBuilder.builder().build()
        private val variantInfo = mockk<AndroidCompactedVariantData>(relaxed = true)
        private val projectDirectory = project.layout.projectDirectory
        private val configFileFinder = mockk<VariantConfigurationFileFinder>()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkObject(VariantConfigurationValidator)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkObject(VariantConfigurationValidator)
        }
    }

    @Test(expected = RuntimeException::class)
    fun `build with no config file finders should throw exception`() {
        EmbraceVariantConfigurationFileBuildStrategy.build(
            variantInfo,
            projectDirectory,
            emptyList()
        )
    }

    @Test
    fun `build with config file finders, but file was not found, it should not throw exception because file is optional`() {
        every { configFileFinder.fetchFile() } returns null

        val variantConfiguration = EmbraceVariantConfigurationFileBuildStrategy.build(
            variantInfo,
            projectDirectory,
            listOf(configFileFinder)
        )

        assertNull(variantConfiguration)
    }

    @Test
    fun `build VariantConfiguration successfully`() {
        val expectedVariantConfig = mockk<EmbraceVariantConfig>()
        every { configFileFinder.fetchFile() } returns
            File(javaClass.classLoader.getResource("config_file_expected.json").file)
        every {
            VariantConfigurationValidator.validate(
                any(),
                VariantConfigurationValidator.VariantConfigurationSourceType.CONFIG_FILE,
                any()
            )
        } returns expectedVariantConfig

        val variantConfiguration = EmbraceVariantConfigurationFileBuildStrategy.build(
            variantInfo,
            projectDirectory,
            listOf(configFileFinder)
        )

        assertEquals(expectedVariantConfig, variantConfiguration)
        verify { configFileFinder.fetchFile() }
        verify {
            VariantConfigurationValidator.validate(
                any(),
                VariantConfigurationValidator.VariantConfigurationSourceType.CONFIG_FILE,
                any()
            )
        }
    }

    @Test
    fun `for empty embrace-config json file it should not throw exception because all values are now optional`() {
        every { configFileFinder.fetchFile() } returns
            File(javaClass.classLoader.getResource("config_file_empty.json").file)

        val variantConfiguration = EmbraceVariantConfigurationFileBuildStrategy.build(
            variantInfo,
            projectDirectory,
            listOf(configFileFinder)
        )

        verify { configFileFinder.fetchFile() }
        assertNotNull(variantConfiguration)
    }
}
