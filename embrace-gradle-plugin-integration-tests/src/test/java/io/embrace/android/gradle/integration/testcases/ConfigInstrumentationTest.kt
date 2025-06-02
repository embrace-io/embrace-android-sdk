package io.embrace.android.gradle.integration.testcases

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.gradle.integration.framework.AssertionInterface
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.integration.framework.smali.SmaliConfigReader
import io.embrace.android.gradle.integration.framework.smali.SmaliMethod
import io.embrace.android.gradle.integration.framework.smali.SmaliParser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ConfigInstrumentationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val variants = listOf("debug", "release")
    private val classNames = listOf(
        "BaseUrlConfigImpl",
        "EnabledFeatureConfigImpl",
        "NetworkCaptureConfigImpl",
        "OtelLimitsConfigImpl",
        "ProjectConfigImpl",
        "RedactionConfigImpl",
        "SessionConfigImpl",
    ).map {
        "/io/embrace/android/embracesdk/internal/config/instrumented/$it"
    }

    @Test
    fun `config default instrumentation`() {
        rule.runTest(
            fixture = "android-with-code",
            task = "assembleRelease",
            additionalArgs = listOf("-x", "lintVitalRelease"),
            projectType = ProjectType.ANDROID,
            assertions = { projectDir ->
                verifyInstrumentedConfig(projectDir, "instrumented-config-default.json")
            }
        )
    }

    @Test
    fun `config non-default instrumentation`() {
        rule.runTest(
            fixture = "android-with-code",
            task = "assembleRelease",
            additionalArgs = listOf("-x", "lintVitalRelease"),
            projectType = ProjectType.ANDROID,
            setup = { projectDir ->
                // copy embrace-config.json with non-default values to project directory
                File(projectDir, "src/main/embrace-config.json").outputStream().buffered().use {
                    ResourceReader.readResource("config-overrides.json").buffered().copyTo(it)
                }
                // TODO: why is libembrace-native uploaded in this fixture, but not in the rest?
                setupMockResponses(
                    expectedLibs = listOf("libembrace-native.so"),
                    expectedArchs = listOf("x86_64", "x86", "armeabi-v7a", "arm64-v8a"),
                    expectedVariants = listOf("debug", "release")
                )
            },
            assertions = { projectDir ->
                verifyInstrumentedConfig(projectDir, "instrumented-config-overrides.json")
            }
        )
    }

    private fun AssertionInterface.verifyInstrumentedConfig(projectDir: File, resName: String) {
        verifyBuildTelemetryRequestSent(variants)
        val reader = SmaliConfigReader()
        val smaliFiles = reader.readSmaliFiles(projectDir, classNames)
        val parser = SmaliParser()
        val cfg = reader.readExpectedConfig(resName)
        var buildIdMethod: SmaliMethod? = null

        cfg.values.forEach { expected ->
            val file = smaliFiles.single { it.name == "${expected.className}.smali" }
            val observed = parser.parse(file, expected.methods)

            val obs = if (observed.className == "ProjectConfigImpl") {
                buildIdMethod = observed.methods.single { it.signature.contains("getBuildId") }
                observed.copy(methods = observed.methods.filterNot { it == buildIdMethod })
            } else {
                observed
            }
            val exp = if (observed.className == "ProjectConfigImpl") {
                expected.copy(methods = expected.methods.filterNot { it.signature.contains("getBuildId") })
            } else {
                expected
            }
            assertEquals(exp, obs)
        }

        // build ID is non-deterministic, test independently
        val buildId = checkNotNull(buildIdMethod?.returnValue)
        verifyJvmMappingRequestsSent(appIds = listOf("abcde"), buildIds = listOf(buildId))
    }
}
