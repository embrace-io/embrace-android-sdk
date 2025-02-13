package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.AssertionInterface
import io.embrace.android.gradle.integration.framework.IntegrationTestDefaults
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.file
import io.embrace.android.gradle.network.FormPart
import io.embrace.android.gradle.network.validateBodyApiToken
import io.embrace.android.gradle.network.validateBodyAppId
import io.embrace.android.gradle.network.validateBodyBuildId
import io.embrace.android.gradle.network.validateMappingFile
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompressedFileUploadTaskIntegrationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `upload compressed file`() {
        var expectedFileContents: String? = null
        rule.runTest(
            fixture = "file-upload-simple",
            setup = { projectDir ->
                assertTrue(projectDir.file("input.txt").exists())
                expectedFileContents = projectDir.file("input.txt").readText()
            },
            assertions = {
                assertFileUploaded(expectedFileContents, EmbraceEndpoint.PROGUARD)
            }
        )
    }

    @Test
    fun `skips upload when file is missing`() {
        rule.runTest(
            fixture = "file-upload-missing-input",
            expectedOutcome = TaskOutcome.NO_SOURCE,
            assertions = {
                verifyNoRequestsSent(EmbraceEndpoint.PROGUARD)
            }
        )
    }

    @Test
    fun `configure endpoint`() {
        var expectedFileContents: String? = null
        rule.runTest(
            fixture = "file-upload-set-endpoint",
            setup = { projectDir ->
                assertTrue(projectDir.file("input.txt").exists())
                expectedFileContents = projectDir.file("input.txt").readText()
            },
            assertions = {
                assertFileUploaded(expectedFileContents, EmbraceEndpoint.NDK)
            }
        )
    }

    private fun AssertionInterface.assertFileUploaded(expectedFileContents: String?, endpoint: EmbraceEndpoint) {
        val request = fetchRequest(endpoint)
        assertHeaders(request, "multipart/form-data", "abcde")

        val parts: List<FormPart> = readMultipartRequest(request)
        parts[0].validateBodyAppId(IntegrationTestDefaults.APP_ID)
        parts[1].validateBodyApiToken(IntegrationTestDefaults.API_TOKEN)
        parts[2].validateBodyBuildId()
        assertEquals(IntegrationTestDefaults.BUILD_ID, parts[2].data)

        parts[3].validateMappingFile("my-filename.txt")
        assertEquals(expectedFileContents, parts[3].data)
    }
}
