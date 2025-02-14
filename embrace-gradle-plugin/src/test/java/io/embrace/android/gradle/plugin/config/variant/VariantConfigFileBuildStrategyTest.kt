package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.fakes.FakeConfigFileDirectory
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.gradle.api.file.Directory
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VariantConfigFileBuildStrategyTest {

    private lateinit var projectDirectory: Directory

    @Before
    fun setup() {
        projectDirectory = FakeConfigFileDirectory()
    }

    @Test(expected = RuntimeException::class)
    fun `build with no config file finders should throw exception`() {
        buildVariantConfig(
            variantInfo,
            projectDirectory,
            emptyList()
        )
    }

    @Test
    fun `config file not found by config finder does not throw exception`() {
        val variantConfiguration = buildVariantConfig(
            variantInfo,
            projectDirectory,
            listOf(VariantConfigurationFileFinder(projectDirectory, listOf()))
        )

        assertNull(variantConfiguration)
    }

    companion object {
        val variantInfo = AndroidCompactedVariantData(
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
