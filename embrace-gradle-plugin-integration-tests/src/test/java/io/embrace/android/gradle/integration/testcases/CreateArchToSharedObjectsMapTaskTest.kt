package io.embrace.android.gradle.integration.testcases

import io.embrace.android.gradle.integration.framework.PluginIntegrationTestRule
import io.embrace.android.gradle.integration.framework.file
import io.embrace.android.gradle.plugin.tasks.ndk.ArchitecturesToSharedObjectsMap
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateArchToSharedObjectsMapTaskTest {
    @Rule
    @JvmField
    val rule: PluginIntegrationTestRule = PluginIntegrationTestRule()

    @Test
    fun `creates correct architectures to shared objects map`() {
        rule.runTest(
            fixture = "architectures-to-shared-objects-test",
            setup = { projectDir ->
                assertTrue(projectDir.file("testArchitecturesDir").exists())
            },
            assertions = { projectDir ->
                val output = projectDir.file("build/output.json")
                val deserializedFile = MoshiSerializer().fromJson(output.readText(), ArchitecturesToSharedObjectsMap::class.java)
                assertTrue(deserializedFile.architecturesToSharedObjects["arm64-v8a"]?.first()?.contains("libtest.so") ?: false)
            }
        )
    }
}
