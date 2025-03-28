package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.buildFile
import io.embrace.android.gradle.integration.framework.file
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InjectSharedObjectFilesTaskIntegrationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `map is injected correctly`() {
        rule.runTest(
            fixture = "inject-shared-object-files",
            assertions = { projectDir ->
                val symbolsPath = "generated-embrace-resources/values/ndk_symbols.xml"
                val symbols = projectDir.buildFile(symbolsPath).bufferedReader().use { it.readText() }
                val expectedResXml = projectDir.file("expected/ndk_symbols.xml").bufferedReader().use { it.readText() }
                assertEquals(expectedResXml, symbols)
            }
        )
    }

    @Test
    fun `an error is thrown when the map can't be deserialized`() {
        rule.runTest(
            fixture = "inject-shared-object-files",
            setup = { projectDir ->
                projectDir.file("architecturesMap.json").writeText("invalid json")
            },
            expectedExceptionMessage = "Failed to read the architectures to hashed shared object files map: Failed to deserialize object",
            assertions = {
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `don't throw an error when failBuildOnUploadErrors is disabled`() {
        rule.runTest(
            fixture = "inject-shared-object-files",
            additionalArgs = listOf("-PfailBuildOnUploadErrors=false"),
            setup = { projectDir ->
                projectDir.file("architecturesMap.json").writeText("invalid json")
            },
            assertions = {
                verifyNoUploads()
            }
        )
    }
}
