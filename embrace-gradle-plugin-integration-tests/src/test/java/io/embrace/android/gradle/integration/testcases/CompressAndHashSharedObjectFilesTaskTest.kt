package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.file
import io.embrace.android.gradle.plugin.hash.calculateSha1ForFile
import io.embrace.android.gradle.plugin.tasks.ndk.ArchitecturesToHashedSharedObjectFilesMap
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/*
 * Given an input directory structure:
 * architecturesDirectory/
 * ├── arm64-v8a/
 * │   ├── libexample1.so
 * │   └── libexample2.so
 * └── armeabi-v7a/
 *     ├── libexample1.so
 *     └── libexample2.so
 *
 * The task will:
 * 1. Create compressed files in:
 * compressedSharedObjectFilesDirectory/
 * ├── arm64-v8a/
 * │   ├── libexample1.so (compressed)
 * │   └── libexample2.so (compressed)
 * └── armeabi-v7a/
 *     ├── libexample1.so (compressed)
 *     └── libexample2.so (compressed)
 *
 * 2. Output a JSON map where:
 *    - Keys are architecture names (e.g., "arm64-v8a")
 *    - Values are maps where:
 *      - Keys are shared object filenames (e.g., "libexample1.so")
 *      - Values are SHA1 hashes of the compressed files
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

class CompressAndHashSharedObjectFilesTaskTest {

    private val expectedSharedObjectFiles = listOf("libemb-donuts.so", "libemb-crisps.so")
    private val expectedArchitectures = listOf("x86_64", "x86", "armeabi-v7a", "arm64-v8a")

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `map output is correct`() {
        rule.runTest(
            fixture = "compress-and-hash-native-libs",
            setup = { projectDir ->
                assertTrue(projectDir.file("testArchitecturesDir").exists())
            },
            assertions = { projectDir ->
                val deserializedOutputMap = MoshiSerializer().fromJson(
                    projectDir.file("build/output.json").readText(),
                    ArchitecturesToHashedSharedObjectFilesMap::class.java
                ).architecturesToHashedSharedObjectFiles

                expectedArchitectures.forEach { architecture ->
                    expectedSharedObjectFiles.forEach { sharedObjectFile ->
                        val sha1Hash = deserializedOutputMap[architecture]?.get(sharedObjectFile)
                        val compressedSoFile = projectDir.file("build/compressedSharedObjectFiles/$architecture/$sharedObjectFile")
                        assertTrue(sha1Hash == calculateSha1ForFile(compressedSoFile))
                    }
                }
            }
        )
    }

    @Test
    fun `verify compression reduces folder size`() {
        rule.runTest(
            fixture = "compress-and-hash-native-libs",
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
}
