package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.file
import io.embrace.android.gradle.plugin.hash.calculateSha1ForFile
import io.embrace.android.gradle.plugin.tasks.ndk.ArchitecturesToHashedSharedObjectFilesMap
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompressAndHashSharedObjectFilesTaskTest {

    private val expectedArchitectures = listOf("arm64-v8a", "armeabi-v7a")
    private val expectedSharedObjectFiles = listOf("libexample1.so", "libexample2.so")

    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `files are compressed and the map output is correct`() {
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
}
