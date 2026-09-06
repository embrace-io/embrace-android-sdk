package io.embrace.startup.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ArtifactManifestTest {

    @Test
    fun `unknown, clean and dirty states with the Python's digest and wording`() {
        val repo = Files.createTempDirectory("repo")
        val doc = Files.createDirectories(repo.resolve("claude-output")).resolve("brief.html")
        Files.writeString(doc, "<title>Brief</title>")
        val manifest = ArtifactManifest(repo)

        assertEquals(2, manifest.check(repo.resolve("claude-output/nope.html")).first)
        val (unknownCode, unknownText) = manifest.check(doc)
        assertEquals(1, unknownCode)
        assertTrue(unknownText.startsWith("UNKNOWN  brief.html\n  Never recorded as published from here."))
        assertEquals("no artifacts recorded yet", manifest.list())

        val recorded = manifest.record(doc, "https://claude.ai/code/artifact/abc", "2026-09-02T02:30:00")
        assertEquals("recorded brief.html -> https://claude.ai/code/artifact/abc (${ArtifactManifest.digest(doc)})", recorded)
        assertTrue(Files.exists(manifest.file))
        val (cleanCode, cleanText) = manifest.check(doc)
        assertEquals(0, cleanCode)
        assertTrue(cleanText.startsWith("CLEAN    brief.html\n  url: https://claude.ai/code/artifact/abc\n"))
        assertTrue(cleanText.contains("(2026-09-02T02:30:00)"))
        assertTrue(manifest.list().startsWith("clean   brief.html"))

        Files.writeString(doc, "<title>Brief v2</title>")
        val (dirtyCode, dirtyText) = manifest.check(doc)
        assertEquals(0, dirtyCode)
        assertTrue(dirtyText.startsWith("DIRTY    brief.html"))
        assertTrue(dirtyText.contains("do NOT replace this file from the published copy"))
        assertTrue(manifest.list().startsWith("DIRTY   brief.html"))

        assertEquals(16, ArtifactManifest.digest(doc).length)
    }
}
