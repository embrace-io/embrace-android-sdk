package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.integration.framework.smali.SmaliConfigReader
import io.embrace.android.gradle.integration.framework.smali.SmaliMethod
import io.embrace.android.gradle.integration.framework.smali.SmaliParser
import io.embrace.android.gradle.plugin.util.INVALID_BUILD_ID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class BytecodeInstrumentationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    private val classNames = listOf(
        "/com/example/app/WebViewClientFixture",
        "/com/example/app/OnClickListenerFixture",
        "/com/example/app/OnLongClickListenerFixture",
        "/okhttp3/OkHttpClient\$Builder",
        "/com/example/app/FcmServiceFixture",
    )

    /**
     * Covers:
     * - bytecode is instrumented on the `release` build type
     * - `buildVariantFilter` disables bytecode instrumentation for the `uninstrumented` build type
     * - the invalid build ID is instrumented when mapping file upload is disabled
     *
     * Globally and individually disabling features is covered by `InstrumentationBehaviorImplTest`.
     */
    @Test
    fun `bytecode instrumentation`() {
        rule.runTest(
            fixture = "android-instrumentation",
            tasks = listOf("assembleRelease", "assembleUninstrumented"),
            additionalArgs = listOf("-Pembrace.disableMappingFileUpload=true"),
            projectType = ProjectType.ANDROID,
            setup = { setupEmptyHandshakeResponse() },
            assertions = { projectDir ->
                verifyBytecodeInstrumented(projectDir, "release", "bytecode-instrumentation-enabled.json")
                verifyBytecodeInstrumented(projectDir, "uninstrumented", "bytecode-instrumentation-disabled.json")
                assertEquals(INVALID_BUILD_ID, readInstrumentedBuildId(projectDir))
            },
        )
    }

    private fun verifyBytecodeInstrumented(projectDir: File, buildType: String, resName: String) {
        val reader = SmaliConfigReader()
        val smaliFiles = reader.readSmaliFiles(projectDir, classNames, buildType)
        val parser = SmaliParser()
        val cfg = reader.readExpectedConfig(resName)

        cfg.values.forEach { expected ->
            val file = smaliFiles.single { it.name == "${expected.className}.smali" }
            val observed = parser.parse(file, expected.methods)
            assertEquals(expected, observed)
        }
    }

    private fun readInstrumentedBuildId(projectDir: File): String? {
        val file = SmaliConfigReader().readSmaliFiles(
            projectDir,
            listOf("/io/embrace/android/embracesdk/internal/config/instrumented/ProjectConfigImpl"),
        ).single()
        return SmaliParser().parse(file, listOf(SmaliMethod("getBuildId()Ljava/lang/String;")))
            .methods
            .single()
            .returnValue
    }
}
