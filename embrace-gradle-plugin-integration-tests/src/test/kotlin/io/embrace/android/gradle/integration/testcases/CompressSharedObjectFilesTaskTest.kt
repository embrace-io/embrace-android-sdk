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
    fun `compresses shared object files`() {
        rule.runTest(
            fixture = "compress-shared-object-files",
            tasks = listOf("compressTask"),
            setup = { projectDir ->
                assertTrue(projectDir.file("testArchitecturesDir").exists())
                // files nested inside an architecture directory are ignored
                projectDir.file("testArchitecturesDir/arm64-v8a/nestedDirectory").mkdirs()
                projectDir.file("testArchitecturesDir/arm64-v8a/nestedDirectory/libexample.so").writeText("some content")
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

                expectedArchitectures.forEach { architecture ->
                    expectedSharedObjectFiles.forEach { sharedObjectFile ->
                        val compressedFile = projectDir.file("build/compressedSharedObjectFiles/$architecture/$sharedObjectFile")
                        assertTrue(compressedFile.exists())
                    }
                }

                assertTrue(
                    projectDir.file("build/compressedSharedObjectFiles/arm64-v8a/")
                        .listFiles { file -> file.name == "nestedDirectory" || file.name == "libexample.so" }
                        .orEmpty()
                        .isEmpty(),
                )
            },
        )
    }
}
