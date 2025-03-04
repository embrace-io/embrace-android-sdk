package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.ApkDisassembler
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.integration.framework.findArtifact
import io.embrace.android.gradle.integration.framework.smali.SmaliMethod
import io.embrace.android.gradle.integration.framework.smali.SmaliParser
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class ConfigInstrumentationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val variants = listOf("debug", "release")

    @Test
    fun assembleRelease() {
        rule.runTest(
            fixture = "android-with-code",
            task = "assembleRelease",
            projectType = ProjectType.ANDROID,
            assertions = { projectDir ->
                verifyBuildTelemetryRequestSent(variants)
                verifyJvmMappingRequestsSent(1)

                val apk = findArtifact(projectDir, "build/outputs/apk/release/", ".apk")
                val decodedApk = ApkDisassembler().disassembleApk(apk)

                val smaliFiles = decodedApk.getSmaliFiles(
                    listOf("/io/embrace/android/embracesdk/internal/config/instrumented/ProjectConfigImpl")
                )
                val parser = SmaliParser()
                val methods = parser.parse(smaliFiles.single())
                val observedMethods = methods.filterNot {
                    it.signature.contains("getBuildId")
                }

                val expected = listOf(
                    SmaliMethod("getAppId()Ljava/lang/String;", "abcde"),
                    SmaliMethod("getBuildFlavor()Ljava/lang/String;", ""),
                    SmaliMethod("getBuildType()Ljava/lang/String;", "release"),
                )

                observedMethods.zip(expected).forEach { (observed, expected) ->
                    assert(observed == expected) {
                        "Expected $expected but found $observed"
                    }
                }

                // build ID is non-deterministic, test independently
                assertNotNull(methods.single { it.signature.contains("getBuildId") }.returnValue)
            }
        )
    }
}
