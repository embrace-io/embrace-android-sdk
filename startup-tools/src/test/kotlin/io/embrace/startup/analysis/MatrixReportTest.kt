package io.embrace.startup.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * No frozen golden exists for the matrix report (it needs cell-state run directories and none
 * survived), so this checks the port against hand-derived expectations: the summary definitions
 * (median, index p90, index IQR, pass medians in string-sorted key order), the pass-state flag, the
 * version table's "newest" rule, and the factor table's reference lookup.
 */
class MatrixReportTest {

    private fun cell(run: Path, id: String, version: String, device: String, passes: Map<String, List<Double>>): Map<Path, Double> {
        val dir = run.resolve(id.replace("|", "__").replace("=", "-"))
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("cell-state.json"),
            """{"cell": {"id": "$id", "version": "$version", "device": "$device"}, "build_type": "benchmark",
                "apk_sha256": "0123456789abcdefFEDCBA"}""",
        )
        val windows = LinkedHashMap<Path, Double>()
        passes.forEach { (pass, values) ->
            val pdir = Files.createDirectories(dir.resolve(pass))
            values.forEachIndexed { i, v ->
                val trace = pdir.resolve("iter${i.toString().padStart(3, '0')}.perfetto-trace")
                Files.writeString(trace, "")
                windows[trace] = v
            }
        }
        return windows
    }

    @Test
    fun `summaries, pass-state flag, version table and factor table on a synthetic run`() {
        val run = Files.createTempDirectory("vfm")
        val windows = HashMap<Path, Double>()
        val ref91 = mapOf("pass1" to listOf(50.0, 52.0, 54.0, 56.0), "pass2" to listOf(70.0, 72.0, 74.0, 76.0))
        val ref92 = mapOf("pass1" to listOf(40.0, 41.0, 42.0, 43.0), "pass2" to listOf(40.5, 41.5, 42.5, 43.5))
        windows += cell(run, "mid|9.1.0|reference", "9.1.0", "mid", ref91)
        windows += cell(run, "mid|9.2.0|reference", "9.2.0", "mid", ref92)
        windows += cell(run, "mid|9.2.0|compile=none", "9.2.0", "mid", mapOf("pass1" to listOf(60.0, 62.0)))
        windows += cell(run, "entry|9.2.0|install=fresh", "9.2.0", "entry", mapOf("pass1" to listOf(90.0)))
        cell(run, "mid|local|reference", "local", "mid", emptyMap()) // no traces: skipped

        val (cells, notes) = MatrixReport.collect(run, { windows[it] }, "emb-sdk-start")
        assertEquals(listOf("SKIP mid|local|reference: no window values (emb-sdk-start missing?)"), notes)
        assertEquals(4, cells.size)
        val s91 = cells.getValue("mid|9.1.0|reference").summary
        assertEquals(8, s91.n)
        assertEquals(63.0, s91.median, 0.0)
        assertEquals(76.0, s91.p90, 0.0) // index int(0.9*8)=7 -> the last of eight sorted values
        assertEquals(76.0, s91.max, 0.0)
        assertEquals(72.0 - 52.0, s91.iqr, 0.0) // all[6] - all[2]
        assertEquals(mapOf("pass1" to 53.0, "pass2" to 73.0), s91.passMedians)
        assertEquals("0123456789ab", cells.getValue("mid|9.1.0|reference").apkSha12)

        val text = MatrixReport.report(cells, "emb-sdk-start", notes)
        val lines = text.lines()
        assertEquals("SKIP mid|local|reference: no window values (emb-sdk-start missing?)", lines[0])
        assertTrue(text.contains("instrument: emb-sdk-start   (absolute values are build-type specific)"))
        // pass-state flag: spread 20 > iqr 20? no (equal) -> no flag for 9.1.0; 9.2.0 spread 0.5 <= iqr -> no flag
        val row91 = lines.first { it.startsWith("mid|9.1.0|reference") }
        assertEquals(
            "mid|9.1.0|reference" + " ".repeat(58 - "mid|9.1.0|reference".length) + "    8     63.0     76.0     76.0  53 73",
            row91,
        )
        assertTrue(lines.any { it.startsWith("entry|9.2.0|install=fresh") && it.trimEnd().endsWith("  90") })

        // Version table: no local cell, so "newest" is the first reference key (sorted map -> mid|9.1.0|reference).
        val versionHeader = lines.indexOf("=== VERSION TABLE (reference cell) ===")
        assertTrue(versionHeader > 0)
        assertEquals(
            "version".padEnd(14) + "med".padStart(9) + "p90".padStart(9) + "max".padStart(9) + "delta vs newest".padStart(18),
            lines[versionHeader + 1],
        )
        assertEquals("9.2.0              41.8     43.5     43.5        -21.2 ms (-34%)", lines[versionHeader + 2])
        assertEquals("9.1.0              63.0     76.0     76.0         +0.0 ms (+0%)", lines[versionHeader + 3])

        // Factor table: mid compile=none vs mid|9.2.0|reference (41.75); entry has no reference cell.
        val factorHeader = lines.indexOf("=== FACTOR TABLE (vs the same version's reference cell) ===")
        assertEquals(
            "9.2.0         install=fresh                      90.0        --   no reference cell",
            lines[factorHeader + 2],
        )
        assertEquals(
            "9.2.0         compile=none                       61.0      41.8        +19.2 ms (+46%)",
            lines[factorHeader + 3],
        )
        assertTrue(text.trimEnd().endsWith("build types or devices except as labelled tier replication."))
    }

    @Test
    fun `a local reference cell is the version-table baseline, and pass keys sort as strings`() {
        val passes = (1..10).associate { "pass$it" to listOf(it.toDouble(), it + 1.0) }
        val s = MatrixReport.summarize(passes)!!
        assertEquals(
            listOf("pass1", "pass10", "pass2", "pass3", "pass4", "pass5", "pass6", "pass7", "pass8", "pass9"),
            s.passMedians.keys.toList(),
        )
        assertEquals(20, s.n)

        val run = Files.createTempDirectory("vfm")
        val windows = HashMap<Path, Double>()
        windows += cell(run, "mid|9.2.0|reference", "9.2.0", "mid", mapOf("pass1" to listOf(40.0, 42.0)))
        windows += cell(run, "mid|local|reference", "local", "mid", mapOf("pass1" to listOf(30.0, 32.0)))
        val (cells, _) = MatrixReport.collect(run, { windows[it] }, "emb-sdk-start")
        val lines = MatrixReport.report(cells, "emb-sdk-start").lines()
        val header = lines.indexOf("=== VERSION TABLE (reference cell) ===")
        assertEquals("local              31.0     32.0     32.0         +0.0 ms (+0%)", lines[header + 2])
        assertEquals("9.2.0              41.0     42.0     42.0        +10.0 ms (+32%)", lines[header + 3])
    }
}
