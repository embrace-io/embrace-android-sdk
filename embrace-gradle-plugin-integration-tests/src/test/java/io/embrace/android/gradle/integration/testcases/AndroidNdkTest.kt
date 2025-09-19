package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.integration.framework.smali.SmaliConfigReader
import io.embrace.android.gradle.integration.framework.smali.SmaliMethod
import io.embrace.android.gradle.integration.framework.smali.SmaliParser
import io.embrace.android.gradle.plugin.tasks.ndk.ArchitecturesToHashedSharedObjectFilesMap
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Base64

class AndroidNdkTest {
    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val defaultExpectedVariants = listOf("release")
    private val variantsSentInBuildTelemetry = listOf("debug", "release")
    private val defaultExpectedLibs = listOf("libemb-donuts.so", "libemb-crisps.so")
    private val defaultExpectedArchs = listOf("x86_64", "x86", "armeabi-v7a", "arm64-v8a")

    @Test
    fun buildCMake() {
        rule.runTest(
            fixture = "android-cmake",
            task = "build",
            projectType = ProjectType.ANDROID,
            setup = {
                setupMockResponses(
                    defaultExpectedLibs,
                    defaultExpectedArchs,
                    defaultExpectedVariants
                )
            },
            assertions = {
                verifyBuildTelemetryRequestSent(variantsSentInBuildTelemetry)
                verifyHandshakes(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
                verifyUploads(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
            }
        )
    }

    @Test
    fun buildNdkBuild() {
        rule.runTest(
            fixture = "android-ndk-build",
            task = "build",
            projectType = ProjectType.ANDROID,
            setup = {
                setupMockResponses(
                    defaultExpectedLibs,
                    defaultExpectedArchs,
                    defaultExpectedVariants
                )
            },
            assertions = {
                verifyBuildTelemetryRequestSent(variantsSentInBuildTelemetry)
                verifyHandshakes(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
                verifyUploads(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
            }
        )
    }

    @Test
    fun `symbols are injected through ASM`() {
        rule.runTest(
            fixture = "android-cmake-with-code",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            setup = {
                setupMockResponses(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
            },
            assertions = { projectDir ->
                // Read and parse the smali file containing the injected symbols
                val smaliFile = SmaliConfigReader().readSmaliFiles(
                    projectDir,
                    listOf("/io/embrace/android/embracesdk/internal/config/instrumented/Base64SharedObjectFilesMapImpl")
                ).first()

                // Get the return value of the getBase64SharedObjectFilesMap method
                val method = SmaliParser().parse(
                    smaliFile,
                    listOf(SmaliMethod("getBase64SharedObjectFilesMap()Ljava/lang/String;"))
                ).methods.first()

                // Decode the base64 string into a map
                val json = Base64.getDecoder().decode(method.returnValue).toString(Charsets.UTF_8)
                val symbols = MoshiSerializer().fromJson(json, ArchitecturesToHashedSharedObjectFilesMap::class.java).symbols

                // Verify all expected architectures and libraries are present
                defaultExpectedArchs.forEach { arch ->
                    assertTrue(symbols.containsKey(arch))
                    defaultExpectedLibs.forEach { lib ->
                        assertTrue(symbols[arch]?.containsKey(lib) ?: false)
                    }
                }
            }
        )
    }

    @Test
    fun `send 3rd party native library symbols even when the project has no native libraries`() {
        rule.runTest(
            fixture = "android-3rd-party-symbols",
            task = "build",
            projectType = ProjectType.ANDROID,
            setup = {
                setupMockResponses(
                    defaultExpectedLibs,
                    defaultExpectedArchs,
                    defaultExpectedVariants
                )
            },
            assertions = {
                verifyBuildTelemetryRequestSent(variantsSentInBuildTelemetry)
                verifyHandshakes(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
                verifyUploads(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
            }
        )
    }

    // If besides the project .so files, there are also 3rd party libraries with native code, we should send the symbols for them as well.
    @Test
    fun `send local and 3rd party library symbols`() {
        val expectedLibs = defaultExpectedLibs + "libemb-asado.so"
        rule.runTest(
            fixture = "android-local-and-3rd-party-symbols",
            task = "build",
            projectType = ProjectType.ANDROID,
            setup = {
                setupMockResponses(expectedLibs, defaultExpectedArchs, defaultExpectedVariants)
            },
            assertions = {
                verifyBuildTelemetryRequestSent(variantsSentInBuildTelemetry)
                verifyHandshakes(expectedLibs, defaultExpectedArchs, defaultExpectedVariants)
                verifyUploads(expectedLibs, defaultExpectedArchs, defaultExpectedVariants)
            }
        )
    }

    @Test
    fun `don't upload symbols if not requested by the backend`() {
        rule.runTest(
            fixture = "android-cmake",
            task = "build",
            projectType = ProjectType.ANDROID,
            setup = {
                setupMockResponses(
                    expectedLibs = emptyList(),
                    expectedArchs = emptyList(),
                    expectedVariants = defaultExpectedVariants
                )
            },
            assertions = {
                verifyBuildTelemetryRequestSent(variantsSentInBuildTelemetry)
                verifyHandshakes(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `partial upload when some symbols are not requested by the backend`() {
        val expectedLibs = listOf("libemb-donuts.so")
        val expectedArchs = listOf("arm64-v8a")
        rule.runTest(
            fixture = "android-cmake",
            task = "build",
            projectType = ProjectType.ANDROID,
            setup = {
                setupMockResponses(
                    expectedLibs = expectedLibs,
                    expectedArchs = expectedArchs,
                    expectedVariants = defaultExpectedVariants
                )
            },
            assertions = {
                verifyBuildTelemetryRequestSent(variantsSentInBuildTelemetry)
                verifyHandshakes(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
                verifyUploads(expectedLibs, expectedArchs, defaultExpectedVariants)
            }
        )
    }

    @Test
    fun `ndk disabled test`() {
        rule.runTest(
            fixture = "android-cmake-ndk-disabled",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variantsSentInBuildTelemetry)
                verifyNoHandshakes()
                verifyNoUploads()
                verifyJvmMappingRequestsSent(1)
            }
        )
    }

    @Test
    fun `debug builds should not upload symbols`() {
        rule.runTest(
            fixture = "android-cmake",
            task = "assembleDebug",
            projectType = ProjectType.ANDROID,
            assertions = {
                verifyBuildTelemetryRequestSent(variantsSentInBuildTelemetry)
                verifyNoHandshakes()
                verifyNoUploads()
                verifyJvmMappingRequestsSent(0)
            }
        )
    }
}
