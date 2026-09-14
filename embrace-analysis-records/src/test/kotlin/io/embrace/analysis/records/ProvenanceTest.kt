package io.embrace.analysis.records

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * [Provenance.load] against a run directory built up file by file: absent, present under either
 * name, both names at once, and nested one or more directories deep, so the walk-and-sort behaviour
 * is pinned rather than assumed.
 */
class ProvenanceTest {

    private lateinit var runDir: Path

    @Before
    fun setUp() {
        runDir = Files.createTempDirectory("provenance-run")
    }

    @Test
    fun `load returns null when the run has no provenance file`() {
        Files.writeString(runDir.resolve("other.json"), """{"unrelated": true}""")

        assertNull(Provenance.load(runDir))
    }

    @Test
    fun `run-metadata json alone is returned with its own name`() {
        Files.writeString(runDir.resolve("run-metadata.json"), """{"source": "fleet-campaign"}""")

        val found = Provenance.load(runDir)

        assertEquals("run-metadata.json", found!!.second)
        assertEquals("fleet-campaign", found.first.getValue("source").jsonPrimitive.content)
    }

    @Test
    fun `cell-state json wins when both provenance files are present`() {
        Files.writeString(runDir.resolve("run-metadata.json"), """{"source": "fleet-campaign"}""")
        Files.writeString(runDir.resolve("cell-state.json"), """{"source": "matrix-cell"}""")

        val found = Provenance.load(runDir)

        assertEquals("cell-state.json", found!!.second)
        assertEquals("matrix-cell", found.first.getValue("source").jsonPrimitive.content)
    }

    @Test
    fun `a provenance file nested in a subdirectory is still found`() {
        val nested = Files.createDirectories(runDir.resolve("pass1").resolve("iter003"))
        Files.writeString(nested.resolve("run-metadata.json"), """{"source": "nested-fleet-campaign"}""")

        val found = Provenance.load(runDir)

        assertEquals("run-metadata.json", found!!.second)
        assertEquals("nested-fleet-campaign", found.first.getValue("source").jsonPrimitive.content)
    }

    @Test
    fun `two nested copies resolve to the lexically first path`() {
        val first = Files.createDirectories(runDir.resolve("pass1"))
        val second = Files.createDirectories(runDir.resolve("pass2"))
        Files.writeString(first.resolve("cell-state.json"), """{"source": "pass1"}""")
        Files.writeString(second.resolve("cell-state.json"), """{"source": "pass2"}""")

        val found = Provenance.load(runDir)

        assertEquals("cell-state.json", found!!.second)
        assertEquals("pass1", found.first.getValue("source").jsonPrimitive.content)
    }
}
