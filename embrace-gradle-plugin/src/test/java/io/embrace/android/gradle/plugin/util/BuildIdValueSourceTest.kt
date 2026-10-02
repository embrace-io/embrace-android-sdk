package io.embrace.android.gradle.plugin.util

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildIdValueSourceTest {

    private val project = ProjectBuilder.builder().build()

    @Test
    fun `obtain returns distinct UUIDs for different variant names`() {
        val id1 = project.providers.of(BuildIdValueSource::class.java) {
            it.parameters.getVariantName().set("release")
        }.get()

        val id2 = project.providers.of(BuildIdValueSource::class.java) {
            it.parameters.getVariantName().set("debug")
        }.get()

        assertNotEquals("Build IDs for different variants must be distinct", id1, id2)
    }

    @Test
    fun `obtain returns Embrace UUID format (32-char uppercase hex)`() {
        val id = project.providers.of(BuildIdValueSource::class.java) {
            it.parameters.getVariantName().set("release")
        }.get()

        assertTrue(
            "Build ID must be 32-char uppercase hex, was: $id",
            id.matches(Regex("[A-F0-9]{32}")),
        )
    }

    @Test
    fun `buildIdProvider returns invalid build ID when a unique ID is not required`() {
        val provider = project.providers.buildIdProvider("debug", requiresUniqueId = false)
        assertEquals(INVALID_BUILD_ID, provider.get())
        assertEquals(INVALID_BUILD_ID, provider.get())
    }

    @Test
    fun `buildIdProvider returns unique build IDs when required`() {
        val release = project.providers.buildIdProvider("release", requiresUniqueId = true).get()
        val staging = project.providers.buildIdProvider("staging", requiresUniqueId = true).get()

        assertTrue(release.matches(Regex("[A-F0-9]{32}")))
        assertNotEquals(INVALID_BUILD_ID, release)
        assertNotEquals(release, staging)
    }
}
