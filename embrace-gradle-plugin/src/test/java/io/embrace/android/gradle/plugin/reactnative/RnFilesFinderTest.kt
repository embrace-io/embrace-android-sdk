package io.embrace.android.gradle.plugin.reactnative

import io.embrace.android.gradle.plugin.Logger
import io.embrace.android.gradle.plugin.tasks.reactnative.RnFilesFinder
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RnFilesFinderTest {

    private val mockLogger = mockk<Logger<RnFilesFinder>>(relaxed = true)

    private val variantName = "demoRelease"
    private val buildDir = File("build")
    private val bundleDirGeneric = File(buildDir, "generated/assets/react/$variantName")
        .also { it.mkdirs() }

    @Test
    fun `getReactNativeSourcemapFilePath - custom bundle asset name`() {
        val reactProps = mapOf(
            "bundleAssetName" to "my-game.bundle"
        )

        val finder = RnFilesFinder(reactProps, File(""), mockLogger)
        val observed = finder.getReactNativeSourcemapFilePath("myFlavor")

        val expected = "generated/sourcemaps/react/myFlavor/my-game.bundle.map"
        assertEquals(expected, observed)
    }

    @Test
    fun `getReactNativeSourcemapFilePath - default bundle asset name`() {
        val reactProps = null

        val finder = RnFilesFinder(reactProps, buildDir, mockLogger)
        val observed = finder.getReactNativeSourcemapFilePath("myFlavor")

        val expected = "generated/sourcemaps/react/myFlavor/index.android.bundle.map"
        assertEquals(expected, observed)
    }

    @Test
    fun `getEmbraceSourcemapFilePath - log deprecated commands if present`() {
        val reactProps = mapOf(
            "extraPackagerArgs" to listOf("a --sourcemap-output", "b")
        )

        val finder = RnFilesFinder(reactProps, buildDir, mockLogger)
        finder.getEmbraceSourcemapFilePath()

        verify { mockLogger.warn(any()) }
    }

    @Test
    fun `getBundleFile - default bundleAssetDirectory (from task), default bundleAssetName`() {
        val bundleFile = File(bundleDirGeneric, "index.android.bundle")
        bundleFile.createNewFile()

        val finder = RnFilesFinder(null, buildDir, mockLogger)
        val observed = finder.getBundleFile()

        val expectedPath = bundleFile.path
        assertEquals(expectedPath, observed?.path)
    }
}
