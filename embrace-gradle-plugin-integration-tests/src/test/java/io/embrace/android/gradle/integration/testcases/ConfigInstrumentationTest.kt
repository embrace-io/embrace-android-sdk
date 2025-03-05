package io.embrace.android.gradle.integration.testcases

import com.squareup.moshi.Moshi
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.gradle.integration.framework.ApkDisassembler
import io.embrace.android.gradle.integration.framework.AssertionInterface
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.integration.framework.findArtifact
import io.embrace.android.gradle.integration.framework.smali.ExpectedSmaliConfig
import io.embrace.android.gradle.integration.framework.smali.SmaliMethod
import io.embrace.android.gradle.integration.framework.smali.SmaliParser
import okio.buffer
import okio.source
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
            },
            assertions = { projectDir ->
                verifyInstrumentedConfig(projectDir, "instrumented-config-overrides.json")
            }
        )
    }

    private fun AssertionInterface.verifyInstrumentedConfig(projectDir: File, resName: String) {
        verifyBuildTelemetryRequestSent(variants)
        val smaliFiles = readSmaliFiles(projectDir)
        val parser = SmaliParser()
        val cfg = readExpectedConfig(resName)
        var buildIdMethod: SmaliMethod? = null

        cfg.values.forEach { expected ->
            val file = smaliFiles.single { it.name == "${expected.className}.smali" }
            val observed = parser.parse(file)

            val obs = if (observed.className == "ProjectConfigImpl") {
                buildIdMethod = observed.methods.single { it.signature.contains("getBuildId") }
                observed.copy(methods = observed.methods.filterNot { it == buildIdMethod })
            } else {
                observed
            }
            assertEquals(expected, obs)
        }

        // build ID is non-deterministic, test independently
        val buildId = checkNotNull(buildIdMethod?.returnValue)
        verifyJvmMappingRequestsSent(appIds = listOf("abcde"), buildIds = listOf(buildId))
    }

    private fun readSmaliFiles(projectDir: File): List<File> {
        val apk = findArtifact(projectDir, "build/outputs/apk/release/", ".apk")
        val decodedApk = ApkDisassembler().disassembleApk(apk)
        val smaliFiles = decodedApk.getSmaliFiles(classNames)
        return smaliFiles
    }

    private fun readExpectedConfig(resName: String): ExpectedSmaliConfig {
        val adapter = Moshi.Builder().build().adapter(ExpectedSmaliConfig::class.java)
        val buffer = ResourceReader.readResource(resName).source().buffer()
        return checkNotNull(adapter.fromJson(buffer))
    }
}
