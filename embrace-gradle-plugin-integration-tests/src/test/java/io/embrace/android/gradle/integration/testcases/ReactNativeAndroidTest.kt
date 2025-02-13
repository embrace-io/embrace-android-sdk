package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.ApkDisassembler
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import org.junit.Assert.assertNotNull
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
            assertions = { projectDir ->
                verifyBuildTelemetryRequestSent(defaultExpectedVariants)
                verifyHandshakes(defaultExpectedLibs, defaultExpectedArchs, defaultExpectedVariants)
                verifyUploads(handshakeLibs, handshakeArchs, defaultExpectedVariants)

                val filename = "app/build/outputs/apk/release/app-release.apk"
                verifyBundleIdInjection(File(projectDir, filename))
            }
        )
    }

    private fun verifyBundleIdInjection(apkFile: File) {
        val decodedApk = ApkDisassembler().disassembleApk(apkFile)
        val bundleId = decodedApk.getStringResource("emb_rn_bundle_id")
        assertNotNull(bundleId)
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
}
