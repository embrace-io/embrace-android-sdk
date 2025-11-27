package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.file
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests the hashing of compressed shared object files.
 * Given compressed files in:
 * compressedSharedObjectFiles/
 * ├── arm64-v8a/
 * │   ├── libexample1.so (compressed)
 * │   └── libexample2.so (compressed)
 * └── armeabi-v7a/
 *     ├── libexample1.so (compressed)
 *     └── libexample2.so (compressed)
 *
 * The task will create a JSON map where:
 * - Keys are architecture names (e.g., "arm64-v8a")
 * - Values are maps where:
 *   - Keys are shared object filenames (e.g., "libexample1.so")
 *   - Values are SHA1 hashes of the compressed files
 * {
 *   "arm64-v8a": {
 *     "libexample1.so": "2a21dc0b99017d5db5960b80d94815a0fe0f3fc2",
 *     "libexample2.so": "3b32ed1c88128e6ec4b71b93a4926a1bf1f4gd3"
 *   },
 *   "armeabi-v7a": {
 *     "libexample1.so": "4c43fe2d77239f7fd5a71c91b85926b2g2g5he4",
 *     "libexample2.so": "5d54gf3e66340g8ge6b82da2c96a37c3h3h6if5"
 *   }
 * }
 */
class HashSharedObjectFilesTaskTest {

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `map output is correct for expectedSharedObjectFiles`() {
        rule.runTest(
            fixture = "hash-shared-object-files",
            task = "hashTask",
            setup = { projectDir ->
                assertTrue(projectDir.file("compressedSharedObjectFiles").exists())
            },
            assertions = { projectDir ->
                assertEquals(
                    projectDir.file("expectedOutput.json").bufferedReader().use { it.readText() },
                    projectDir.file("build/output.json").bufferedReader().use { it.readText() }
                )
            }
        )
    }

    /*
     * The following test cases should never happen.
     * CompressSharedObjectFilesTask should fail if the input directory has any problems, so this task wouldn't be executed.
     * I'm adding this check in case for some reason the output of the compression task is incorrect.
     */
    @Test
    fun `an error is thrown when compressedSharedObjectFiles contain files other than shared objects`() {
        rule.runTest(
            fixture = "hash-shared-object-files",
            task = "hashTask",
            setup = { projectDir ->
                // delete architecture directories
                projectDir.file("compressedSharedObjectFiles").listFiles()?.forEach { it.deleteRecursively() }
                // create a file that is not a .so file
                projectDir.file("compressedSharedObjectFiles/someArch").mkdirs()
                projectDir.file("compressedSharedObjectFiles/someArch/notASharedObject.txt").writeText("not a .so file :)")
            },
            expectedExceptionMessage = "Shared object files not found",
            assertions = {
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `an error is thrown when compressedSharedObjectFilesDirectory does not contain directories`() {
        rule.runTest(
            fixture = "hash-shared-object-files",
            task = "hashTask",
            setup = { projectDir ->
                // delete architecture directories
                projectDir.file("compressedSharedObjectFiles").listFiles()?.forEach { it.deleteRecursively() }
                // create a file instead of a directory
                projectDir.file("compressedSharedObjectFiles/aFile.txt").writeText("not a directory")
            },
            expectedExceptionMessage = "Compressed shared object files directory does not contain any architecture directories",
            assertions = {
                verifyNoUploads()
            }
        )
    }

    @Test
    fun `don't throw an error when failBuildOnUploadErrors is disabled`() {
        rule.runTest(
            fixture = "hash-shared-object-files",
            task = "hashTask",
            additionalArgs = listOf("-PfailBuildOnUploadErrors=false"),
            setup = { projectDir ->
                // delete architecture directories
                projectDir.file("compressedSharedObjectFiles").listFiles()?.forEach { it.deleteRecursively() }
                // create a file instead of a directory
                projectDir.file("compressedSharedObjectFiles/aFile.txt").writeText("not a directory")
            },
            assertions = {
                verifyNoUploads()
            }
        )
    }
}
