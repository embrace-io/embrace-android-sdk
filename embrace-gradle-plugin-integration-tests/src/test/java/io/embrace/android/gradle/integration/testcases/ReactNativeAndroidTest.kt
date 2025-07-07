package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.smali.SmaliConfigReader
import io.embrace.android.gradle.integration.framework.smali.SmaliMethod
import io.embrace.android.gradle.integration.framework.smali.SmaliParser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReactNativeAndroidTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val defaultExpectedVariants = listOf("debug", "release")
    private val defaultExpectedLibs = listOf(
        "libappmodules.so",
        "libc++_shared.so",
        "libembrace-native.so",
        "libfbjni.so",
        "libhermes.so",
        "libhermestooling.so",
        "libimagepipeline.so",
        "libjsi.so",
        "libnative-filters.so",
        "libnative-imagetranscoder.so",
        "libreactnative.so"
    )

    private val defaultExpectedArchs = listOf("x86_64", "x86", "armeabi-v7a", "arm64-v8a")

    @Test
    fun `react native ndk upload test`() {
        val handshakeLibs = listOf(
            "libfbjni.so",
            "libhermes.so",
            "libhermestooling.so",
        )
        val handshakeArchs = listOf("arm64-v8a", "armeabi-v7a")
        rule.runTest(
            fixture = "react-native-android",
            androidProjectRoot = "android",
            task = "build",
            setup = { projectDir ->
                installNodeModules(projectDir)
                setupMockResponses(handshakeLibs, handshakeArchs, defaultExpectedVariants)
            },
            assertions = {
                verifyBuildTelemetryRequestSent(defaultExpectedVariants)
                verifyHandshakes(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
                verifyUploads(handshakeLibs, handshakeArchs, defaultExpectedVariants)
            }
        )
    }

    @Test
    fun `react native asm injection test`() {
        rule.runTest(
            fixture = "react-native-android",
            androidProjectRoot = "android",
            task = "assembleRelease",
            setup = { projectDir ->
                setupEmptyHandshakeResponse()
                installNodeModules(projectDir)
            },
            assertions = { projectDir ->
                verifyAsmInjection(File(projectDir, "app"), "27D4D89A18B0426A47151D4888D4E40A")
            }
        )
    }

    @Test
    fun `react native project without bundle or sourcemap`() {
        rule.runTest(
            fixture = "not-react-native-android",
            androidProjectRoot = "android",
            additionalArgs = listOf("-Pandroid.useAndroidX=true"),
            task = "assembleRelease",
            setup = {
                setupEmptyHandshakeResponse()
            },
            assertions = { projectDir ->
                verifyNoUploads()
                verifyAsmInjection(projectDir, null)
            }
        )
    }

    private fun installNodeModules(projectDir: File) {
        val process = ProcessBuilder("npm", "install")
            .directory(projectDir)
            .redirectErrorStream(true) // Merges stderr into stdout
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("npm install failed with exit code $exitCode\n$output")
        }
    }

    private fun verifyAsmInjection(buildDir: File, expectedBundleId: String?) {
        // Read and parse the smali file containing the injected symbols
        val smaliFile = SmaliConfigReader().readSmaliFiles(
            buildDir,
            listOf("/io/embrace/android/embracesdk/internal/config/instrumented/ProjectConfigImpl")
        ).first()

        // Get the return value of the getBase64SharedObjectFilesMap method
        val method = SmaliParser().parse(
            smaliFile,
            listOf(SmaliMethod("getReactNativeBundleId()Ljava/lang/String;"))
        ).methods.first()

        assertEquals(expectedBundleId, method.returnValue)
    }
}
