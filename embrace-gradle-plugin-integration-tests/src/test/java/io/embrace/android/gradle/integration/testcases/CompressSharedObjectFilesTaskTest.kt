package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.file
import org.gradle.testkit.runner.TaskOutcome
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

    @Test
    fun `task is not executed when architecturesDirectory doesn't exist`() {
        rule.runTest(
            fixture = "compress-shared-object-files",
            task = "compressTask",
            setup = { projectDir ->
                projectDir.file("testArchitecturesDir").deleteRecursively()
            },
            expectedOutcome = TaskOutcome.NO_SOURCE,
            assertions = {
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `task is not executed when architecturesDirectory is empty`() {
        rule.runTest(
            fixture = "compress-shared-object-files",
            task = "compressTask",
            setup = { projectDir ->
                // keep testArchitecturesDir but delete its contents
                projectDir.file("testArchitecturesDir").listFiles()?.forEach { it.deleteRecursively() }
            },
            expectedOutcome = TaskOutcome.NO_SOURCE,
            assertions = {
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `task is not executed when architecturesDirectory only contains empty directories `() {
        rule.runTest(
            fixture = "compress-shared-object-files",
            task = "compressTask",
            setup = { projectDir ->
                // delete architecture directories
                projectDir.file("testArchitecturesDir").listFiles()?.forEach { it.deleteRecursively() }
                // add empty directories
                projectDir.file("testArchitecturesDir/someArch").mkdirs()
                projectDir.file("testArchitecturesDir/someArch2").mkdirs()
            },
            expectedOutcome = TaskOutcome.NO_SOURCE,
            assertions = {
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `an error is thrown when architecturesDirectory is not a directory`() {
        rule.runTest(
            fixture = "compress-shared-object-files",
            task = "compressTask",
            setup = { projectDir ->
                // delete testArchitecturesDir
                projectDir.file("testArchitecturesDir").deleteRecursively()
                // create a file instead of a directory
                projectDir.file("testArchitecturesDir").writeText("not a directory")
            },
            expectedExceptionMessage = "Expected an input to be a directory but it was a file.",
            assertions = {
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `an error is thrown when architecturesDirectory does not contain directories`() {
        rule.runTest(
            fixture = "compress-shared-object-files",
            task = "compressTask",
            setup = { projectDir ->
                // delete architecture directories
                projectDir.file("testArchitecturesDir").listFiles()?.forEach { it.deleteRecursively() }
                // create a file instead of a directory
                projectDir.file("testArchitecturesDir/aFile.txt").writeText("not a directory")
            },
            expectedExceptionMessage = "Specified architectures directory does not contain any architecture directories",
            assertions = {
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `an error is thrown when architecture directories contain files other than shared objects`() {
        rule.runTest(
            fixture = "compress-shared-object-files",
            task = "compressTask",
            setup = { projectDir ->
                // delete architecture directories
                projectDir.file("testArchitecturesDir").listFiles()?.forEach { it.deleteRecursively() }
                // create a file that is not a .so file
                projectDir.file("testArchitecturesDir/someArch").mkdirs()
                projectDir.file("testArchitecturesDir/someArch/notASharedObject.txt").writeText("not a .so file :)")
            },
            expectedExceptionMessage = "No shared object files found in architecture directory someArch",
            assertions = {
                verifyNoUploads()
            }
        )
    }
}
