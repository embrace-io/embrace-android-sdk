package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import org.junit.Rule
import org.junit.Test

class AndroidNdkTest {
    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val defaultExpectedVariants = listOf("release")
    private val variantsSentInBuildTelemetry = listOf("debug", "release")
    private val defaultExpectedLibs = listOf("libemb-donuts.so", "libemb-crisps.so")
    private val defaultExpectedArchs = listOf("x86_64", "arm64-v8a")
    private val allArchs = listOf("x86_64", "x86", "armeabi-v7a", "arm64-v8a")

    /**
     * Covers:
     * - local (CMake) and 3rd party (ndk-build) native library symbols are uploaded for the `release` build type
     * - every ABI is uploaded, whereas most fixtures build two ABIs by default to save time
     * - `debug` builds don't upload symbols
     * - `src/nondk/embrace-config.json` disables NDK uploads on the `nondk` build type but still uploads its mapping file
     */
    @Test
    fun `native symbols end to end`() {
        val expectedLibs = defaultExpectedLibs + "libemb-asado.so"
        rule.runTest(
            fixture = "android-local-and-3rd-party-symbols",
            tasks = listOf("assembleDebug", "assembleRelease", "assembleNondk"),
            additionalArgs = listOf("-PabiFilters=${allArchs.joinToString(",")}"),
            projectType = ProjectType.ANDROID,
            setup = {
                setupMockResponses(expectedLibs, allArchs, defaultExpectedVariants)
            },
            assertions = {
                verifyBuildTelemetryRequestSent(listOf("debug", "release", "nondk"))
                verifyHandshakes(expectedLibs, allArchs, defaultExpectedVariants)
                verifyUploads(expectedLibs, allArchs, defaultExpectedVariants)
                // release and nondk are both minified
                verifyJvmMappingRequestsSent(2)
            },
        )
    }

    @Test
    fun `send 3rd party native library symbols even when the project has no native libraries`() {
        rule.runTest(
            fixture = "android-3rd-party-symbols",
            tasks = listOf("build"),
            projectType = ProjectType.ANDROID,
            setup = {
                setupMockResponses(
                    defaultExpectedLibs,
                    defaultExpectedArchs,
                    defaultExpectedVariants,
                )
            },
            assertions = {
                verifyBuildTelemetryRequestSent(variantsSentInBuildTelemetry)
                verifyHandshakes(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
                verifyUploads(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
            },
        )
    }
}
