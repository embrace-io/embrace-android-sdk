package io.embrace.startup.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BorrowedStateAndArmsTest {

    @Test
    fun `borrowed state restores on normal exit and on exception, and recovers a stale marker BEFORE reading`() {
        val dir = Files.createTempDirectory("borrow")
        val marker = dir.resolve("state/pin.marker")
        var pin = "9.2.0"
        val log = ArrayList<String>()
        val state = BorrowedState(marker, { pin }, { pin = it }, { log.add(it) })

        val result = state.use { original ->
            assertEquals("9.2.0", original)
            assertTrue(Files.exists(marker))
            pin = "9.3.0-SNAPSHOT"
            "done"
        }
        assertEquals("done", result)
        assertEquals("9.2.0", pin)
        assertFalse(Files.exists(marker))
        assertEquals(listOf("restored borrowed state (normal exit): 9.2.0"), log)

        log.clear()
        val failing = BorrowedState(marker, { pin }, { pin = it }, { log.add(it) })
        val thrown = runCatching {
            failing.use<String> {
                pin = "local"
                error("boom")
            }
        }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertEquals("9.2.0", pin)
        assertEquals(listOf("restored borrowed state (exception IllegalStateException): 9.2.0"), log)

        // A dead run left a marker holding the true original while the pin still carries its mutation.
        Files.createDirectories(marker.parent)
        Files.writeString(marker, "9.1.0")
        pin = "9.4.0-SNAPSHOT"
        log.clear()
        BorrowedState(marker, { pin }, { pin = it }, { log.add(it) }).use { original ->
            assertEquals("9.1.0", original) // recovered first, then read
        }
        assertEquals("9.1.0", pin)
        assertEquals("RECOVERY: previous run left state unrestored; setting back to 9.1.0", log.first())
    }

    @Test
    fun `verify-arms - identical dex fails conclusively, a bare difference is inconclusive, a control decides`() {
        val dir = Files.createTempDirectory("arms")
        val base = ByteArray(4096) { (it % 251).toByte() }
        val flipped = base.copyOf().also {
            it[100] = 99
            it[2000] = 7
        }
        val rebuilt = base.copyOf().also { it[3000] = 1 }
        val a = apk(dir.resolve("a.apk"), mapOf("classes.dex" to base))
        val b = apk(dir.resolve("b.apk"), mapOf("classes.dex" to flipped))
        val same = apk(dir.resolve("same.apk"), mapOf("classes.dex" to base.copyOf()))
        val control = apk(dir.resolve("control.apk"), mapOf("classes.dex" to rebuilt))
        val structural = apk(dir.resolve("two.apk"), mapOf("classes.dex" to base, "classes2.dex" to base))

        val identical = ArmVerifier.verify(a, same, null)
        assertEquals(1, identical.exitCode)
        assertTrue(identical.text.contains("classes.dex: identical (4096 bytes)"))
        assertTrue(identical.text.contains("FAIL (conclusive)"))

        val bare = ArmVerifier.verify(a, b, null)
        assertEquals(0, bare.exitCode)
        assertTrue(bare.text.contains("classes.dex: DIFFERS - 2 byte positions (0.0488%), sizes 4096 vs 4096, sha "))
        assertTrue(bare.text.contains("INCONCLUSIVE: the arms differ (2 byte positions)"))

        val decided = ArmVerifier.verify(a, b, control)
        assertEquals(0, decided.exitCode)
        assertTrue(decided.text.contains("noise floor from the control (arm A vs its own rebuild): 1 byte positions"))
        assertTrue(decided.text.contains("PASS: the arm difference exceeds the build-noise floor"))

        val drowned = ArmVerifier.verify(a, control, b)
        assertEquals(1, drowned.exitCode)
        assertTrue(drowned.text.contains("FAIL: the arm difference does not exceed the noise floor"))

        val structuralVerdict = ArmVerifier.verify(a, structural, null)
        assertEquals(1, structuralVerdict.exitCode)
        assertTrue(structuralVerdict.text.contains("classes2.dex: present in only one arm - STRUCTURAL difference"))
        assertTrue(structuralVerdict.text.contains("WARNING: a dex file is present in only one arm."))

        assertEquals(3L, ArmVerifier.diffBytes(byteArrayOf(1, 2, 3), byteArrayOf(1, 9, 3, 4, 5)))
    }

    private fun apk(path: Path, entries: Map<String, ByteArray>): Path {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(byteArrayOf(0, 1))
            zip.closeEntry()
        }
        return path
    }
}
