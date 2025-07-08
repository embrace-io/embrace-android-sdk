package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.fakes.FakeConfigFileDirectory
import io.embrace.android.gradle.fakes.FakeSystemWrapper
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.pathString

class BuildVariantConfigFromFileTest {

    @Test(expected = IllegalStateException::class)
    fun `build with no config file finders should throw exception`() {
        buildVariantConfig(
            fakeVariantInfo,
            projectDirectory,
            emptyList()
        )
    }

    @Test
    fun `config file not found by config finder does not throw exception`() {
        val variantConfiguration = buildVariantConfig(
            fakeVariantInfo,
            projectDirectory,
            listOf(VariantConfigurationFileFinder(projectDirectory, listOf()))
        )

        assertNull(variantConfiguration)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bad config file throws exception`() {
        configFile.writeText("not a json")
        buildVariantConfig(
            fakeVariantInfo,
            projectDirectory,
            listOf(fileFinder)
        )
    }

    @Test
    fun `variantConfiguration is built correctly`() {
        val json = """
            {
              "app_id": "abcde",
              "api_token": "12345678901234567890123456789012",
              "ndk_enabled": true
            }
        """.trimIndent()
        configFile.writeText(json)

        val variantConfiguration = buildVariantConfig(
            fakeVariantInfo,
            projectDirectory,
            listOf(fileFinder)
        )

        assertEquals("abcde", variantConfiguration?.appId)
        assertEquals("12345678901234567890123456789012", variantConfiguration?.apiToken)
        assertEquals(true, variantConfiguration?.ndkEnabled)
    }

    @Test
    fun `apiToken is read from environment variable when it is not set in config file`() {
        val json = """
            {
              "app_id": "abcde",
              "ndk_enabled": true
            }
        """.trimIndent()
        configFile.writeText(json)

        val fakeSystemWrapper = FakeSystemWrapper().apply {
            setEnvironmentVariable("EMBRACE_API_TOKEN", "env12345678901234567890123456789")
        }

        val variantConfiguration = buildVariantConfig(
            fakeVariantInfo,
            projectDirectory,
            listOf(fileFinder),
            fakeSystemWrapper
        )

        assertEquals("env12345678901234567890123456789", variantConfiguration?.apiToken)
    }

    @Test
    fun `apiToken is not read from environment variable when it is set in config file`() {
        val json = """
            {
              "api_token": "config78901234567890123456789012"
            }
        """.trimIndent()
        configFile.writeText(json)

        val fakeSystemWrapper = FakeSystemWrapper().apply {
            setEnvironmentVariable("EMBRACE_API_TOKEN", "env12345678901234567890123456789")
        }

        val variantConfiguration = buildVariantConfig(
            fakeVariantInfo,
            projectDirectory,
            listOf(fileFinder),
            fakeSystemWrapper
        )

        assertEquals("config78901234567890123456789012", variantConfiguration?.apiToken)
    }

    @Test
    fun `appId is read from environment variable when it is not set in config file`() {
        val json = """
            {
              "api_token": "config78901234567890123456789012",
              "ndk_enabled": true
            }
        """.trimIndent()
        configFile.writeText(json)

        val fakeSystemWrapper = FakeSystemWrapper().apply {
            setEnvironmentVariable("EMBRACE_APP_ID", "env12")
        }

        val variantConfiguration = buildVariantConfig(
            fakeVariantInfo,
            projectDirectory,
            listOf(fileFinder),
            fakeSystemWrapper
        )

        assertEquals("env12", variantConfiguration?.appId)
    }

    @Test
    fun `appId is not read from environment variable when it is set in config file`() {
        val json = """
            {
              "app_id": "confg"
            }
        """.trimIndent()
        configFile.writeText(json)

        val fakeSystemWrapper = FakeSystemWrapper().apply {
            setEnvironmentVariable("EMBRACE_APP_ID", "env12")
        }

        val variantConfiguration = buildVariantConfig(
            fakeVariantInfo,
            projectDirectory,
            listOf(fileFinder),
            fakeSystemWrapper
        )

        assertEquals("confg", variantConfiguration?.appId)
    }

    companion object {
        private lateinit var configFile: File
        private lateinit var fileFinder: VariantConfigurationFileFinder
        private lateinit var projectDirectory: FakeConfigFileDirectory

        /**
         * Creates a fake project directory with a subdirectory `src` that contains the Embrace config file.
         * We can update the config file directly in the tests to simulate different scenarios.
         */
        private fun createFakeProjectDirectory(): FakeConfigFileDirectory {
            val tempDirectory = Files.createTempDirectory("test")
            Files.createDirectories(tempDirectory.resolve("src"))
            return FakeConfigFileDirectory(tempDirectory.pathString, true).apply {
                subDirectoriesWithConfigFiles.add("src")
            }
        }

        private fun getConfigFileForProjectDirectory(projectDirectory: FakeConfigFileDirectory): File {
            val dirPath = projectDirectory.asFile.toPath()
            return dirPath.resolve("src").resolve("embrace-config.json").toFile()
        }

        // Create the fake project directory and config file finder before running the tests.
        @JvmStatic
        @BeforeClass
        fun setup() {
            projectDirectory = createFakeProjectDirectory()
            configFile = getConfigFileForProjectDirectory(projectDirectory)
            fileFinder = VariantConfigurationFileFinder(projectDirectory, listOf("src"))
        }

        // Clean up the temporary directory after all tests have run.
        @JvmStatic
        @AfterClass
        fun tearDown() {
            // Clean up the temporary directory after tests
            projectDirectory.asFile.deleteRecursively()
        }
    }

    private val fakeVariantInfo = AndroidCompactedVariantData(
        name = "variant-name",
        flavorName = "flavor-name",
        buildTypeName = "buildType-name",
        isBuildTypeDebuggable = false,
        versionName = "1.0",
        productFlavors = listOf("product-flavor", "2nd-product-flavor"),
        sourceMapPath = "source"
    )
}
