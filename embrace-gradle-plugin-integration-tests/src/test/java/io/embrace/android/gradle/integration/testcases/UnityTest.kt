package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.AssertionInterface
import io.embrace.android.gradle.integration.framework.IntegrationTestDefaults
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.network.validateBodyApiToken
import io.embrace.android.gradle.network.validateBodyAppId
import io.embrace.android.gradle.network.validateBodyBuildId
import io.embrace.android.gradle.network.validateMappingFile
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import org.junit.Rule
import org.junit.Test

/**
 * Tests that IL2CPP mapping files are uploaded in a Unity project. Building an actual Unity
 * project would take an unacceptably long time for a unit test, so we fake the important details
 * in our test fixture:
 *
 * 1. The project uses the NDK (as Unity apps always use NDK)
 * 2. embrace.uploadIl2CppMappingFiles=true is set in the project's gradle.properties. Our Unity
 * SDK writes this value usually.
 * 3. A file exists at unityLibrary/src/main/il2cppOutputProject/Source/il2cppOutput/Symbols from
 * the project root. Our Unity SDK copies this file into the Gradle project.
 */
class UnityTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `build unity project`() {
        rule.runTest(
            fixture = "unity-fake-project",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyLineMapRequestSent()
                verifyMethodMapRequestSent()
            }
        )
    }

    /**
     * Verifies the LineNumberMappings.json file is sent
     */
    private fun AssertionInterface.verifyLineMapRequestSent() {
        val request = fetchRequests(EmbraceEndpoint.LINE_MAP).single()
        val parts = readMultipartRequest(request)
        parts[0].validateBodyAppId(IntegrationTestDefaults.APP_ID)
        parts[1].validateBodyApiToken(IntegrationTestDefaults.API_TOKEN)
        parts[2].validateBodyBuildId()
        parts[3].validateMappingFile("LineNumberMappings.json")
    }

    /**
     * Verifies the MethodMap.tsv file is sent
     */
    private fun AssertionInterface.verifyMethodMapRequestSent() {
        val request = fetchRequests(EmbraceEndpoint.METHOD_MAP).single()
        val parts = readMultipartRequest(request)
        parts[0].validateBodyAppId(IntegrationTestDefaults.APP_ID)
        parts[1].validateBodyApiToken(IntegrationTestDefaults.API_TOKEN)
        parts[2].validateBodyBuildId()
        parts[3].validateMappingFile("MethodMap.tsv")
    }
}
