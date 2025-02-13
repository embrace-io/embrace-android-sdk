package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.fakes.FakeConfigFileDirectory
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VariantConfigFileFinderTest {
    private lateinit var projectDir: FakeConfigFileDirectory
    private lateinit var fileFinder: VariantConfigurationFileFinder

    @Before
    fun before() {
        projectDir = FakeConfigFileDirectory()
        projectDir.subDirectoriesWithConfigFiles.add("src")
        fileFinder = VariantConfigurationFileFinder(projectDir, listOf("src"))
    }

    @Test
    fun `return correct file if config file exists`() {
        assertNotNull(fileFinder.fetchFile())
    }

    @Test
    fun `if no file locations then return null`() {
        fileFinder = VariantConfigurationFileFinder(projectDir, emptyList())
        assertNull(fileFinder.fetchFile())
    }

    @Test
    fun `if location exist but file does not exist then return null`() {
        projectDir.subDirectoriesWithConfigFiles.clear()
        assertNull(fileFinder.fetchFile())
    }

    @Test
    fun `find config file in default directory`() {
        val path = "src/main"
        projectDir.subDirectoriesWithConfigFiles.add(path)
        validateConfigFile(defaultConfigurationFileFinder(projectDir), path)
    }

    @Test
    fun `find config file in build type variant directory`() {
        val path = "src/${fakeVariantInfo.buildTypeName}"
        projectDir.subDirectoriesWithConfigFiles.add(path)
        validateConfigFile(variantBuildTypeConfigurationFileFinder(fakeVariantInfo, projectDir), path)
    }

    @Test
    fun `find config file in flavor variant directory`() {
        val path = "src/${fakeVariantInfo.flavorName}"
        projectDir.subDirectoriesWithConfigFiles.add(path)
        validateConfigFile(variantFlavorConfigurationFileFinder(fakeVariantInfo, projectDir), path)
    }

    @Test
    fun `find config file in name variant directory`() {
        val path = "src/${fakeVariantInfo.name}"
        projectDir.subDirectoriesWithConfigFiles.add(path)
        validateConfigFile(variantNameConfigurationFileFinder(fakeVariantInfo, projectDir), path)
    }

    @Test
    fun `config file not found when using product flavor if there are no product flavors`() {
        val variantInfo = fakeVariantInfo.copy(productFlavors = emptyList())
        val fileFinder = variantProductFlavorsConfigurationFileFinder(variantInfo, projectDir)
        assertNull(fileFinder.fetchFile())
    }

    @Test
    fun `config file in first product flavor location used if multiple are defined`() {
        val path = "src/${fakeVariantInfo.productFlavors.first()}"
        fakeVariantInfo.productFlavors.map { "src/$it" }.forEach {
            projectDir.subDirectoriesWithConfigFiles.add(it)
        }

        validateConfigFile(variantProductFlavorsConfigurationFileFinder(fakeVariantInfo, projectDir), path)
    }

    @Test
    fun `test directory for product flavor if first one doesn't exist`() {
        val path = "src/${fakeVariantInfo.productFlavors.last()}"
        projectDir.subDirectoriesWithConfigFiles.add(path)
        validateConfigFile(variantProductFlavorsConfigurationFileFinder(fakeVariantInfo, projectDir), path)
    }

    private fun validateConfigFile(fileFinder: VariantConfigurationFileFinder, path: String) {
        assertEquals(
            "${projectDir.asFile.path}/$path/$VARIANT_CONFIG_FILE_NAME",
            checkNotNull(fileFinder.fetchFile()).path
        )
    }

    private companion object {
        val fakeVariantInfo = AndroidCompactedVariantData(
            name = "variant-name",
            flavorName = "flavor-name",
            buildTypeName = "buildType-name",
            isBuildTypeDebuggable = false,
            versionName = "1.0",
            productFlavors = listOf("product-flavor", "2nd-product-flavor"),
            sourceMapPath = "source"
        )
    }
}
