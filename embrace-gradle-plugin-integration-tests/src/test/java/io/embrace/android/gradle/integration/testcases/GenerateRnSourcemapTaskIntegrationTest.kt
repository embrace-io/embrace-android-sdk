package io.embrace.android.gradle.integration.testcases

import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.FormPart
import io.embrace.android.embracesdk.validateBodyApiToken
import io.embrace.android.embracesdk.validateBodyAppId
import io.embrace.android.embracesdk.validateBodyBuildId
import io.embrace.android.embracesdk.validateMappingFile
import io.embrace.android.gradle.integration.framework.IntegrationTestDefaults
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.buildFile
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import okio.buffer
import okio.gzip
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class GenerateRnSourcemapTaskIntegrationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `react native upload`() {
        rule.runTest(
            fixture = "rn-upload-simple",
            assertions = { projectDir ->
                // 1. assert that the task output was the bundle + sourcemap file zipped as JSON
                verifyOutputFile(projectDir.buildFile("output"))

                // 2. assert that a request took place
                val request = fetchRequest(EmbraceEndpoint.SOURCE_MAP)
                assertHeaders(request, "multipart/form-data", "abcde")

                // 3. assert the multipart form data contains bundle info
                val parts: List<FormPart> = readMultipartRequest(request)
                assertEquals(4, parts.size)
                parts[0].validateBodyAppId(IntegrationTestDefaults.APP_ID)
                parts[1].validateBodyApiToken(IntegrationTestDefaults.API_TOKEN)
                parts[2].validateBodyBuildId()
                parts[3].validateMappingFile("sourcemap.json")
            }
        )
    }

    private fun verifyOutputFile(outputFile: File) {
        // unzip the file - this will fail if the file is not gzipped
        outputFile.source().gzip().buffer().use { source ->
            // deserialize the JSON - this will fail if the JSON is not valid
            val bundleAndSourceMap = MoshiSerializer().fromJson(source.readUtf8(), BundleAndSourceMap::class.java)
            assertEquals("Fake bundle", bundleAndSourceMap.bundle)
            assertEquals("Fake sourcemap", bundleAndSourceMap.sourcemap)
        }
    }
}

@JsonClass(generateAdapter = true)
data class BundleAndSourceMap(val bundle: String, val sourcemap: String)
