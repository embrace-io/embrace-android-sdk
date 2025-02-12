package io.embrace.android.gradle.integration.testcases

import com.github.luben.zstd.ZstdInputStream
import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.buildFile
import io.embrace.android.gradle.integration.framework.file
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FileCompressionTaskIntegrationTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `copies input file and compresses it to output file`() {
        rule.runTest(
            fixture = "file-compression-simple",
            setup = { projectDir ->
                assertTrue(projectDir.file("input.txt").exists())
                assertFalse(projectDir.buildFile("mapping.txt").exists())
            },
            assertions = { projectDir ->
                // assert the task created a gzipped file
                val dst = projectDir.buildFile("mapping.txt")
                assertTrue(dst.exists() && dst.length() > 0)

                val output = ZstdInputStream(dst.inputStream()).bufferedReader().use {
                    it.readText()
                }
                assertEquals("Hello, world!", output)
                val input = projectDir.file("input.txt").readText()
                assertEquals(input, output)
            }
        )
    }

    @Test
    fun `skips compression when input file is missing`() {
        rule.runTest(
            fixture = "file-compression-missing-input",
            expectedOutcome = TaskOutcome.NO_SOURCE,
            assertions = { projectDir ->
                assertFalse(projectDir.file("mapping.txt").exists())
                assertFalse(projectDir.buildFile("mapping.txt").exists())
            }
        )
    }
}
