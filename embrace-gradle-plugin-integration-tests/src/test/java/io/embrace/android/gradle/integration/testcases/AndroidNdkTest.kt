package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.ApkDisassembler
import io.embrace.android.gradle.integration.framework.BundleToolApkBuilder
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.integration.framework.findArtifact
import io.embrace.android.gradle.integration.utils.NdkSymbols
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import okio.ByteString.Companion.decodeBase64
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class AndroidNdkTest {
    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val defaultExpectedVariants = listOf("debug", "release")
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
                verifyBuildTelemetryRequestSent(defaultExpectedVariants)
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
                verifyBuildTelemetryRequestSent(defaultExpectedVariants)
                verifyHandshakes(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
                verifyUploads(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
            }
        )
    }

    // When a 3rd party dependency has native libraries, but the project has no externalNativeBuild block, we don't send symbols (yet).
    @Test
    fun `don't send 3rd party native library symbols when the project has no native libraries`() {
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
                verifyBuildTelemetryRequestSent(defaultExpectedVariants)
                verifyNoHandshakes()
                verifyNoUploads()
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
                verifyBuildTelemetryRequestSent(defaultExpectedVariants)
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
                verifyBuildTelemetryRequestSent(defaultExpectedVariants)
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
                verifyBuildTelemetryRequestSent(defaultExpectedVariants)
                verifyHandshakes(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
                verifyUploads(expectedLibs, expectedArchs, defaultExpectedVariants)
            }
        )
    }

    @Test
    fun `symbols are injected into the APK`() {
        rule.runTest(
            fixture = "android-cmake",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            setup = {
                setupMockResponses(
                    defaultExpectedLibs,
                    defaultExpectedArchs,
                    defaultExpectedVariants
                )
            },
            assertions = { projectDir ->
                val apk = findArtifact(projectDir, "build/outputs/apk/release/", ".apk")
                verifyNdkApkSymbolsInjection(apk)
            }
        )
    }

    @Test
    fun `symbols are injected into the bundle`() {
        rule.runTest(
            fixture = "android-cmake",
            task = "bundleRelease",
            projectType = ProjectType.ANDROID,
            setup = {
                setupMockResponses(
                    defaultExpectedLibs,
                    defaultExpectedArchs,
                    defaultExpectedVariants
                )
            },
            assertions = { projectDir ->
                verifyBundleSymbolsInjection(projectDir)
            }
        )
    }

    @Test
    fun `ndk disabled test`() {
        rule.runTest(
            fixture = "android-cmake-ndk-disabled",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            assertions = { projectDir ->
                verifyBuildTelemetryRequestSent(defaultExpectedVariants)
                verifyNoHandshakes()
                verifyNoUploads()
                verifyJvmMappingRequestsSent(1)
                val apk = findArtifact(projectDir, "build/outputs/apk/release/", ".apk")
                verifyNoSymbolsInjected(apk)
            }
        )
    }

    private fun verifyNoSymbolsInjected(apkFile: File) {
        val decodedApk = ApkDisassembler().disassembleApk(apkFile)
        val resourceName = "emb_ndk_symbols"
        assertNull(decodedApk.getStringResource(resourceName))
    }

    private fun verifyNdkApkSymbolsInjection(apkFile: File) {
        val decodedApk = ApkDisassembler().disassembleApk(apkFile)
        val resourceName = "emb_ndk_symbols"
        val symbols = decodedApk.getStringResource(resourceName)
            ?: error("Resource named '$resourceName' not found")
        validateBase64Symbols(symbols)
    }

    private fun verifyBundleSymbolsInjection(projectDir: File) {
        val bundle = findArtifact(projectDir, "build/outputs/bundle/release/", ".aab")
        val apkFile = BundleToolApkBuilder().generateApkFromBundle(bundle)
        verifyNdkApkSymbolsInjection(apkFile)
    }

    private fun validateBase64Symbols(base64Symbols: String) {
        val symbolString =
            base64Symbols.decodeBase64()?.utf8() ?: error("Failed to decode base64 symbols")

        val symbolsMap = MoshiSerializer().fromJson(symbolString, NdkSymbols::class.java).symbols
            ?: error("Failed to deserialize symbols")

        defaultExpectedArchs.forEach { arch ->
            defaultExpectedLibs.forEach { lib ->
                assertTrue(symbolsMap[arch]?.containsKey(lib) ?: false)
            }
        }
    }
}
