package io.embrace.android.embracesdk.internal.perfetto.cli

import io.embrace.android.embracesdk.internal.perfetto.report.ReportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

internal class CliParserTest {

    @Test
    fun `the usage message names the command, its inputs, and what it does with them`() {
        val usage = TRACE_SPEC.usage
        assertTrue(usage, usage.startsWith("usage: analyseTrace <trace.perfetto.gz> [options]"))
        assertTrue(usage, usage.contains("--format markdown|json|html"))
        assertTrue(usage, usage.contains("default: alongside <trace.perfetto.gz>"))
        assertTrue(usage, usage.contains("--dry-run"))
        assertTrue(usage, usage.endsWith(NOTES))
        assertTrue(usage, COMPARE_SPEC.usage.startsWith("usage: compareIterations <baseline.json> <candidate.json>"))
    }

    @Test
    fun `only a command that measures sections documents the section options`() {
        val usage = TRACE_SPEC.usage
        assertTrue(usage, usage.contains("--operations <a,b,c>"))
        assertTrue(usage, usage.contains("--all-operations"))
        assertFalse(COMPARE_SPEC.usage, COMPARE_SPEC.usage.contains("--operations"))
    }

    @Test
    fun `an input on its own reports every section as markdown, alongside the input, with dry run off`() {
        val options = checkNotNull(parseArgs(TRACE_SPEC, arrayOf("perf/run/trace.perfetto.gz")))

        assertEquals(File("perf/run/trace.perfetto.gz"), options.input)
        assertEquals(emptyList<String>(), options.operations)
        assertTrue(options.allOperations)
        assertEquals(ReportFormat.MARKDOWN, options.format)
        assertEquals(File("perf/run/trace-report.md"), options.output)
        assertFalse(options.dryRun)
        assertTrue(checkNotNull(parseArgs(TRACE_SPEC, arrayOf("--dry-run", TRACE))).dryRun)
    }

    @Test
    fun `the format decides what a derived report is called, and a named file wins over one`() {
        assertEquals(File("a-report.html"), output(TRACE, "--format", "html"))
        assertEquals(File("a-report.json"), output(TRACE, "--format", "json"))
        assertEquals(File(OUTPUT), output(TRACE, "--output", OUTPUT))
    }

    @Test
    fun `sections are named as one comma separated list, or asked for wholesale`() {
        val named = checkNotNull(parseArgs(TRACE_SPEC, arrayOf(TRACE, "--operations", "emb-sdk-start,emb-core-init")))
        assertEquals(listOf("emb-sdk-start", "emb-core-init"), named.operations)
        assertFalse(named.allOperations)

        val all = checkNotNull(parseArgs(TRACE_SPEC, arrayOf("--all-operations", "--format", "html", TRACE)))
        assertEquals(File(TRACE), all.input)
        assertTrue(all.allOperations)
        assertEquals(ReportFormat.HTML, all.format)
    }

    @Test
    fun `a command takes exactly the inputs it names, and only the options it takes`() {
        assertNotNull(parseArgs(COMPARE_SPEC, arrayOf(BASELINE, CANDIDATE)))
        rejects(TRACE_SPEC, arrayOf(), arrayOf("--dry-run"), arrayOf(TRACE, "b.perfetto-trace"))
        rejects(
            COMPARE_SPEC,
            arrayOf(BASELINE),
            arrayOf(BASELINE, CANDIDATE, "extra.json"),
            arrayOf(BASELINE, CANDIDATE, "--all-operations"),
            arrayOf(BASELINE, CANDIDATE, "--operations", "emb-sdk-start"),
        )
    }

    @Test
    fun `an option whose value is missing, unknown, or contradictory is a usage error`() {
        rejects(
            TRACE_SPEC,
            arrayOf(TRACE, "--format"),
            arrayOf(TRACE, "--format", "xml"),
            arrayOf(TRACE, "--operations"),
            arrayOf(TRACE, "--operations", "--output", OUTPUT),
            arrayOf(TRACE, "--operations", "emb-sdk-start,"),
            arrayOf(TRACE, "--output"),
            arrayOf(TRACE, "--operations", "emb-sdk-start", "--all-operations"),
            arrayOf(TRACE, "--all-operations", "--operations", "emb-sdk-start"),
            arrayOf("--unknown", TRACE),
        )
    }

    @Test
    fun `the usage message is asked for by either spelling, anywhere in the arguments`() {
        assertTrue(asksForHelp(arrayOf("--help")))
        assertTrue(asksForHelp(arrayOf(TRACE, "--all-operations", "-h")))
        assertFalse(asksForHelp(arrayOf(TRACE, "--dry-run")))
    }

    private fun output(vararg args: String) = checkNotNull(parseArgs(TRACE_SPEC, arrayOf(*args))).output

    private fun rejects(spec: CliSpec, vararg cases: Array<String>) =
        cases.forEach { args -> assertNull(args.joinToString(" "), parseArgs(spec, args)) }

    private companion object {
        const val TRACE = "a.perfetto-trace"
        const val OUTPUT = "report.json"
        const val BASELINE = "baseline.json"
        const val CANDIDATE = "candidate.json"
        const val NOTES = "Normally run via a script."
        val TRACE_SPEC = CliSpec("analyseTrace", listOf("<trace.perfetto.gz>"), notes = NOTES)
        val COMPARE_SPEC = CliSpec(
            command = "compareIterations",
            inputs = listOf("<baseline.json>", "<candidate.json>"),
            selectsOperations = false,
        )
    }
}
