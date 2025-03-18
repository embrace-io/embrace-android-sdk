package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.buildFile
import io.embrace.android.gradle.integration.framework.file
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NdkUploadTaskIntegrationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `ndk upload`() {
        rule.runTest(
            fixture = "ndk-upload-simple",
            assertions = { projectDir ->
                val symbols = projectDir.buildFile("generated-embrace-resources/values/ndk_symbols.xml")
                val expectedResXml = projectDir.file("expected/ndk_symbols.xml").readText()
                assertEquals(expectedResXml, symbols.readText())
            }
        )
    }
}
