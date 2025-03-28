package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.file
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests the compression of shared object files.
 * Given an input directory structure:
 * testArchitecturesDir/
 * ├── arm64-v8a/
 * │   ├── libexample1.so
 * │   └── libexample2.so
 * └── armeabi-v7a/
 *     ├── libexample1.so
 *     └── libexample2.so
 *
 * The task will create compressed files in:
 * build/compressedSharedObjectFiles/
 * ├── arm64-v8a/
 * │   ├── libexample1.so (compressed)
 * │   └── libexample2.so (compressed)
 * └── armeabi-v7a/
 *     ├── libexample1.so (compressed)
 *     └── libexample2.so (compressed)
 */
class CompressSharedObjectFilesTaskTest {

    private val expectedSharedObjectFiles = listOf("libemb-donuts.so", "libemb-crisps.so")
    private val expectedArchitectures = listOf("x86_64", "x86", "armeabi-v7a", "arm64-v8a")

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `verify compression reduces folder size`() {
        rule.runTest(
            fixture = "compress-shared-object-files",
            task = "compressTask",
            setup = { projectDir ->
                assertTrue(projectDir.file("testArchitecturesDir").exists())
            },
            assertions = { projectDir ->
                val originalSize = projectDir.file("testArchitecturesDir")
                    .walk()
                    .filter { it.isFile }
                    .sumOf { it.length() } // returns size in bytes

                val compressedSize = projectDir.file("build/compressedSharedObjectFiles")
                    .walk()
                    .filter { it.isFile }
                    .sumOf { it.length() } // returns size in bytes

                assertTrue(compressedSize < originalSize)
            }
        )
    }

    @Test
    fun `verify compressed files maintain directory structure`() {
        rule.runTest(
            fixture = "compress-shared-object-files",
            task = "compressTask",
            assertions = { projectDir ->
                expectedArchitectures.forEach { architecture ->
                    expectedSharedObjectFiles.forEach { sharedObjectFile ->
                        val compressedFile = projectDir.file("build/compressedSharedObjectFiles/$architecture/$sharedObjectFile")
                        assertTrue(compressedFile.exists())
                    }
                }
            }
        )
    }
}
