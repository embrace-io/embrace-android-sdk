package io.embrace.android.gradle.plugin.config.variant

import io.embrace.android.gradle.ResourceReader
import io.embrace.android.gradle.fakes.FakeConfigFileDirectory
import io.embrace.android.gradle.plugin.instrumentation.config.model.EmbraceVariantConfig
import io.embrace.android.gradle.plugin.instrumentation.config.model.WebViewLocalConfig
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.util.serialization.JsonSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.io.path.pathString

/**
 * Pins how a user's embrace-config.json is parsed, so a serialization library change can't silently start
 * accepting or rejecting config files that customers already have.
 */
class ConfigFileParsingTest {

    private lateinit var projectDirectory: FakeConfigFileDirectory
    private lateinit var configFile: File
    private lateinit var fileFinder: VariantConfigurationFileFinder

    @Before
    fun setUp() {
        val tempDirectory = Files.createTempDirectory("config-parsing")
        Files.createDirectories(tempDirectory.resolve("src"))
        projectDirectory = FakeConfigFileDirectory(tempDirectory.pathString, true).apply {
            subDirectoriesWithConfigFiles.add("src")
        }
        configFile = tempDirectory.resolve("src").resolve("embrace-config.json").toFile()
        fileFinder = VariantConfigurationFileFinder(projectDirectory, listOf("src"))
    }

    @After
    fun tearDown() {
        projectDirectory.asFile.deleteRecursively()
    }

    @Test
    fun `every field maps to its json key`() {
        val json = ResourceReader.readResourceAsText("config_file_all_fields.json")
        val config = parse(json)
        assertEquals(WebViewLocalConfig.FragmentCapture.REDACT, config?.sdkConfig?.webViewConfig?.fragmentCapture)
        assertEquals(json.minify(), encode(checkNotNull(config)))
    }

    @Test
    fun `empty object gives all null fields`() {
        assertEquals(EmbraceVariantConfig(null, null, null, null, null, json = "{}"), parse("{}"))
    }

    @Test
    fun `explicit nulls are accepted`() {
        val json = """{"app_id": null, "api_token": null, "ndk_enabled": null, "sdk_config": null, "unity": null}"""
        assertEquals(EmbraceVariantConfig(null, null, null, null, null, json = json.minify()), parse(json))
    }

    @Test
    fun `null document gives no config`() {
        assertNull(parse("null"))
    }

    @Test
    fun `quoted number is accepted`() {
        val config = parse("""{"sdk_config": {"networking": {"default_capture_limit": "5"}}}""")
        assertEquals(5, config?.sdkConfig?.networking?.defaultCaptureLimit)
    }

    @Test
    fun `missing required domain field is rejected`() {
        assertRejected("""{"sdk_config": {"networking": {"domains": [{"domain_name": "example.com"}]}}}""")
    }

    @Test
    fun `quoted boolean is accepted`() {
        assertEquals(true, parse("""{"ndk_enabled": "true"}""")?.ndkEnabled)
    }

    @Test
    fun `non-boolean string for boolean is rejected`() {
        assertRejected("""{"ndk_enabled": "yes"}""")
    }

    @Test
    fun `string for object is rejected`() {
        assertRejected("""{"sdk_config": "text"}""")
    }

    @Test
    fun `unknown enum value is rejected`() {
        assertRejected("""{"sdk_config": {"webview": {"fragment_capture": "bogus"}}}""")
    }

    @Test
    fun `unknown key in a list element is rejected`() {
        assertRejected(
            """{"sdk_config": {"networking": {"domains": [{"domain_name": "a", "domain_limit": 1, "x": 1}]}}}""",
        )
    }

    @Test
    fun `comments are rejected`() {
        assertRejected("""{"app_id": "abcde" /* comment */}""")
    }

    @Test
    fun `trailing comma is rejected`() {
        assertRejected("""{"app_id": "abcde",}""")
    }

    @Test
    fun `unknown key error names the key`() {
        val ex = assertRejected("""{"sdk_config": {"taps": {"capture_coordinatez": true}}}""")
        assertTrue(ex.message, ex.message?.contains("unrecognized key") == true)
        assertTrue(ex.message, ex.message?.contains("capture_coordinatez") == true)
    }

    private fun parse(json: String): EmbraceVariantConfig? {
        configFile.writeText(json)
        return buildVariantConfig(fakeVariantInfo, projectDirectory, listOf(fileFinder))
    }

    private fun assertRejected(json: String): IllegalArgumentException =
        assertThrows(IllegalArgumentException::class.java) { parse(json) }

    private fun encode(config: EmbraceVariantConfig): String {
        val stream = ByteArrayOutputStream()
        JsonSerializer().toJson(config, EmbraceVariantConfig.serializer(), stream)
        return stream.toString(Charsets.UTF_8.name())
    }

    private fun String.minify(): String = replace(Regex("\\s"), "")

    private val fakeVariantInfo = AndroidCompactedVariantData(
        name = "variant-name",
        flavorName = "flavor-name",
        buildTypeName = "buildType-name",
        isBuildTypeDebuggable = false,
        productFlavors = emptyList(),
        sourceMapPath = "source",
    )
}
