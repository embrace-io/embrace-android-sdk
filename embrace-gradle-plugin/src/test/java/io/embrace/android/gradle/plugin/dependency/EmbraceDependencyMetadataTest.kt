package io.embrace.android.gradle.plugin.dependency

import io.embrace.embrace_gradle_plugin.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbraceDependencyMetadataTest {

    @Test
    fun `verify correct values`() {
        val customVersion = "customVersion"

        val defaultCore = EmbraceDependencyMetadata.Core()
        val customVersionCore = EmbraceDependencyMetadata.Core(customVersion)

        assertEquals("io.embrace", defaultCore.group)
        assertEquals("io.embrace", customVersionCore.group)
        assertEquals("embrace-android-sdk", defaultCore.artefact)
        assertEquals("embrace-android-sdk", customVersionCore.artefact)
        assertEquals(BuildConfig.VERSION, defaultCore.version)
        assertEquals(customVersion, customVersionCore.version)
    }
}
