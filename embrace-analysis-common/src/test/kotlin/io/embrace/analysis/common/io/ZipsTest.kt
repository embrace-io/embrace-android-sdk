package io.embrace.analysis.common.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Round trip and zip-slip coverage for [Zips], exercised as a caller sees it: pack a tree, unpack it
 * back, merge into an existing archive without disturbing what is already there, and refuse an entry
 * that tries to climb out of the target directory.
 */
class ZipsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var sourceRoot: Path
    private lateinit var archive: Path

    @Before
    fun setUp() {
        sourceRoot = tempFolder.newFolder("source").toPath()
        Files.createDirectories(sourceRoot.resolve("nested"))
        Files.writeString(sourceRoot.resolve("a.txt"), "alpha")
        Files.writeString(sourceRoot.resolve("nested/b.txt"), "bravo")
        archive = tempFolder.newFolder("out").toPath().resolve("bundle.zip")
    }

    @Test
    fun `packTree and unpack round trip the tree byte for byte`() {
        Zips.packTree(sourceRoot, archive)

        val restored = Zips.unpack(archive, tempFolder.newFolder("restored").toPath())

        assertEquals("alpha", Files.readString(restored.resolve("a.txt")))
        assertEquals("bravo", Files.readString(restored.resolve("nested/b.txt")))
    }

    @Test
    fun `unpackToTemp restores into a fresh directory named after the archive`() {
        Zips.packTree(sourceRoot, archive)

        val restored = Zips.unpackToTemp(archive)

        assertTrue(restored.fileName.toString().startsWith("bundle-"))
        assertEquals("alpha", Files.readString(restored.resolve("a.txt")))
    }

    @Test
    fun `merge keeps every existing entry and adds only the new file names`() {
        val fileA = tempFolder.newFile("a.txt").toPath().also { Files.writeString(it, "first") }
        val fileB = tempFolder.newFile("b.txt").toPath().also { Files.writeString(it, "second") }
        Zips.pack(listOf(fileA, fileB), archive)

        val fileAConflict = tempFolder.newFolder("later").toPath().resolve("a.txt")
            .also { Files.writeString(it, "REPLACED - should be ignored") }
        val fileC = tempFolder.newFile("c.txt").toPath().also { Files.writeString(it, "third") }

        val count = Zips.merge(archive, listOf(fileAConflict, fileC))

        assertEquals(3, count)
        val restored = Zips.unpackToTemp(archive)
        assertEquals("first", Files.readString(restored.resolve("a.txt")))
        assertEquals("second", Files.readString(restored.resolve("b.txt")))
        assertEquals("third", Files.readString(restored.resolve("c.txt")))
    }

    @Test
    fun `unpack refuses an entry that climbs out of the target with a dot dot segment`() {
        val malicious = tempFolder.newFolder("malicious").toPath().resolve("evil.zip")
        ZipOutputStream(Files.newOutputStream(malicious)).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.txt"))
            zip.write("gotcha".toByteArray())
            zip.closeEntry()
        }

        val error = runCatching { Zips.unpack(malicious, tempFolder.newFolder("target").toPath()) }.exceptionOrNull()

        assertTrue("expected an IllegalArgumentException, got $error", error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("refusing entry"))
    }
}
