package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.AssertionInterface
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.ProjectType
import io.embrace.android.gradle.integration.framework.SetupInterface
import io.embrace.android.gradle.integration.framework.smali.SmaliConfigReader
import io.embrace.android.gradle.integration.framework.smali.SmaliParser
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
    private val defaultArgs = listOf("-x", "lintVitalRelease")

    private val ndkSetup: SetupInterface.(File) -> Unit = { setupEmptyHandshakeResponse() }

    @Test
    fun `bytecode instrumentation enabled`() {
        rule.runTest(
            fixture = "android-instrumentation",
            task = "assembleRelease",
            additionalArgs = defaultArgs,
            projectType = ProjectType.ANDROID,
            setup = ndkSetup,
            assertions = { projectDir ->
                verifyBytecodeInstrumented(projectDir, "bytecode-instrumentation-enabled.json")
            }
        )
    }

    @Test
    fun `bytecode instrumentation disabled globally`() {
        rule.runTest(
            fixture = "android-instrumentation",
            task = "assembleRelease",
            additionalArgs = defaultArgs.plus(listOf("-P", "globalDisable=true")),
            projectType = ProjectType.ANDROID,
            setup = ndkSetup,
            assertions = { projectDir ->
                verifyBytecodeInstrumented(projectDir, "bytecode-instrumentation-disabled.json")
            }
        )
    }

    @Test
    fun `bytecode instrumentation disabled individually`() {
        rule.runTest(
            fixture = "android-instrumentation",
            task = "assembleRelease",
            additionalArgs = defaultArgs.plus(listOf("-P", "individualDisable=true")),
            projectType = ProjectType.ANDROID,
            setup = ndkSetup,
            assertions = { projectDir ->
                verifyBytecodeInstrumented(projectDir, "bytecode-instrumentation-disabled.json")
            }
        )
    }

    @Test
    fun `bytecode instrumentation disabled by variant`() {
        rule.runTest(
            fixture = "android-instrumentation",
            task = "assembleRelease",
            additionalArgs = defaultArgs.plus(listOf("-P", "disableByVariant=true")),
            projectType = ProjectType.ANDROID,
            setup = ndkSetup,
            assertions = { projectDir ->
                verifyBytecodeInstrumented(projectDir, "bytecode-instrumentation-disabled.json")
            }
        )
    }

    private fun AssertionInterface.verifyBytecodeInstrumented(projectDir: File, resName: String) {
        verifyJvmMappingRequestsSent(1)

        val reader = SmaliConfigReader()
        val smaliFiles = reader.readSmaliFiles(projectDir, classNames)
        val parser = SmaliParser()
        val cfg = reader.readExpectedConfig(resName)

        cfg.values.forEach { expected ->
            val file = smaliFiles.single { it.name == "${expected.className}.smali" }
            val observed = parser.parse(file, expected.methods)
            assertEquals(expected, observed)
        }
    }
}
