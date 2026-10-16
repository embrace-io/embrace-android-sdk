package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.integration.framework.smali.SmaliConfigReader
import io.embrace.android.gradle.integration.framework.smali.SmaliMethod
import io.embrace.android.gradle.integration.framework.smali.SmaliParser
import io.embrace.android.gradle.plugin.tasks.ndk.ArchitecturesToHashedSharedObjectFilesMap
import io.embrace.android.gradle.plugin.util.INVALID_BUILD_ID
import io.embrace.android.gradle.plugin.util.serialization.JsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64

class ConfigInstrumentationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val variants = listOf("debug", "release", "overrides")
    private val classNames = listOf(
        "BaseUrlConfigImpl",
        "EnabledFeatureConfigImpl",
        "NetworkCaptureConfigImpl",
        "OtelLimitsConfigImpl",
        "ProjectConfigImpl",
        "RedactionConfigImpl",
    ).map {
        "/io/embrace/android/embracesdk/internal/config/instrumented/$it"
    }

    /**
     * Covers:
     * - default config values are instrumented on the minified `release` build type
     * - non-default values from `src/overrides/embrace-config.json` are instrumented on the `overrides` build type
     * - the instrumented build ID on `release` matches the one sent with the mapping file upload
     * - hashes of native libs built with CMake are instrumented on `release`
     */
    @Test
    fun `config instrumentation`() {
        rule.runTest(
            fixture = "android-with-code",
            tasks = listOf("assembleRelease", "assembleOverrides"),
            projectType = ProjectType.ANDROID,
            setup = {
                setupEmptyHandshakeResponse()
            },
            assertions = { projectDir ->
                verifyBuildTelemetryRequestSent(variants)
                val buildId = verifyInstrumentedConfig(projectDir, "release", "instrumented-config-default.json")
                verifyJvmMappingRequestsSent(appIds = listOf("abcde"), buildIds = listOf(buildId))
                verifyInstrumentedConfig(projectDir, "overrides", "instrumented-config-overrides.json")
                verifyInstrumentedSharedObjectFiles(projectDir)
            },
        )
    }

    /**
     * Asserts the base64-encoded map of native lib hashes was instrumented on `release` for every ABI and lib.
     */
    private fun verifyInstrumentedSharedObjectFiles(projectDir: File) {
        val smaliFile = SmaliConfigReader().readSmaliFiles(
            projectDir,
            listOf("/io/embrace/android/embracesdk/internal/config/instrumented/Base64SharedObjectFilesMapImpl"),
        ).single()
        val method = SmaliParser().parse(
            smaliFile,
            listOf(SmaliMethod("getBase64SharedObjectFilesMap()Ljava/lang/String;")),
        ).methods.single()

        val json = Base64.getDecoder().decode(method.returnValue).toString(Charsets.UTF_8)
        val symbols = JsonSerializer().fromJson(json, ArchitecturesToHashedSharedObjectFilesMap.serializer()).symbols
        listOf("x86_64", "arm64-v8a").forEach { arch ->
            listOf("libemb-donuts.so", "libemb-crisps.so").forEach { lib ->
                assertTrue("Missing $lib for $arch", symbols[arch]?.containsKey(lib) == true)
            }
        }
    }

    /**
     * Asserts the instrumented config matches [resName] and returns the instrumented build ID, which is
     * non-deterministic so is asserted separately.
     */
    private fun verifyInstrumentedConfig(projectDir: File, buildType: String, resName: String): String {
        val reader = SmaliConfigReader()
        val smaliFiles = reader.readSmaliFiles(projectDir, classNames, buildType)
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

        val buildId = checkNotNull(buildIdMethod?.returnValue)
        assertNotEquals(INVALID_BUILD_ID, buildId)
        return buildId
    }
}
