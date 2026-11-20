package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.config.TestMatrix
import io.embrace.android.gradle.integration.framework.AssertionInterface
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.smali.SmaliConfigReader
import io.embrace.android.gradle.integration.framework.smali.SmaliMethod
import io.embrace.android.gradle.integration.framework.smali.SmaliParser
import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReactNativeAndroidTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `react native release build`() {
        rule.runTest(
            fixture = "react-native-android",
            testMatrix = TestMatrix.MiddleVersion,
            androidProjectRoot = "android",
            tasks = listOf("assembleRelease"),
            setup = {
                setupMockResponses(emptyList(), emptyList(), listOf("release"))
            },
            assertions = { projectDir ->
                verifyBuildTelemetryRequestSent(listOf("debug", "release"), testMatrix = TestMatrix.MiddleVersion)
                assertEquals(1, fetchRequests(EmbraceEndpoint.SOURCE_MAP).size)
                verifyAsmInjection(File(projectDir, "app"), BUNDLE_ID)
            },
        )
    }

    private fun AssertionInterface.verifyAsmInjection(buildDir: File, expectedBundleId: String?) {
        val smaliFile = SmaliConfigReader(fileSystem).readSmaliFiles(
            buildDir,
            listOf("/io/embrace/android/embracesdk/internal/config/instrumented/ProjectConfigImpl"),
        ).first()

        val method = SmaliParser(fileSystem).parse(
            smaliFile,
            listOf(SmaliMethod("getReactNativeBundleId()Ljava/lang/String;")),
        ).methods.first()

        assertEquals(expectedBundleId, method.returnValue)
    }

    private companion object {
        const val BUNDLE_ID = "B3338A366B74EB89C112C8BB13C1B570"
    }
}
