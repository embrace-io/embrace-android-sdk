package io.embrace.analysis.cli

import io.embrace.analysis.benchmark.IterationTrace
import io.embrace.analysis.startup.StartupMetrics
import io.embrace.analysis.startup.StartupMetricsSource
import io.embrace.analysis.startup.StartupWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal class AnalyzeCommandTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var out: ByteArrayOutputStream
    private lateinit var err: ByteArrayOutputStream
    private lateinit var traces: File
    private lateinit var shell: File
    private lateinit var source: FakeMetricsSource
    private lateinit var command: AnalyzeCommand
    private lateinit var pinned: Path
    private var installs = 0

    @Before
    fun setUp() {
        pinned = tmp.root.toPath().resolve("cache/v1/mac-arm64/trace_processor_shell")
        installs = 0
        out = ByteArrayOutputStream()
        err = ByteArrayOutputStream()
        traces = tmp.newFolder("traces")
        shell = tmp.newFile("trace_processor_shell").apply { setExecutable(true) }
        source = FakeMetricsSource()
        command = AnalyzeCommand(
            out = PrintStream(out, true, Charsets.UTF_8),
            err = PrintStream(err, true, Charsets.UTF_8),
            repo = tmp.root.toPath(),
            clock = Clock.fixed(Instant.parse("2026-11-09T08:13:34Z"), ZoneOffset.UTC),
            installTraceProcessor = {
                installs++
                pinned
            },
            metricsSource = { shellPath ->
                source.shell = shellPath
                source
            },
        )
    }

    @Test
    fun `the report is printed and written to a file named for the start time, traces read in iteration order`() {
        File(traces, "StartupBenchmarks_coldStartup_iter001_b.perfetto-trace").writeText("")
        File(traces, "StartupBenchmarks_coldStartup_iter000_a.perfetto-trace").writeText("")
        File(traces, "notes.txt").writeText("")
        val outputDir = File(tmp.root, "summaries")

        val code = command.run(listOf("--trace-processor", shell.path, "--output-dir=${outputDir.path}", traces.path))

        assertEquals(0, code)
        // the named binary is used, and the pinned one isn't installed
        assertEquals(shell.toPath(), source.shell)
        assertEquals(0, installs)
        assertEquals(listOf("iter000_a", "iter001_b"), source.read.map { it.shortName })
        assertTrue(source.closed)
        val summary = File(outputDir, "startup-analysis-2026-11-09-081334.txt")
        val text = summary.readText()
        assertTrue(text.startsWith("startup analysis started 2026-11-09 08:13:34\ntraces dir: ${traces.absolutePath}\n"))
        assertTrue(text.contains("traces analyzed: 2\n"))
        assertEquals("$text\nsummary written to $summary\n", out.toString(Charsets.UTF_8))
    }

    @Test
    fun `by default the pinned trace processor is used and the summary goes to claude-output in the repository`() {
        File(traces, "iter000.perfetto-trace").writeText("")

        assertEquals(0, command.run(listOf("--all-sections", traces.path)))

        assertEquals(pinned, source.shell)
        assertEquals(1, installs)
        val summary = File(tmp.root, "claude-output/startup-analysis-2026-11-09-081334.txt")
        assertTrue(summary.readText().contains("other emb-* sections (all; "))
    }

    @Test
    fun `a pinned trace processor that can't be installed is reported`() {
        File(traces, "iter000.perfetto-trace").writeText("")
        val failing = AnalyzeCommand(
            out = PrintStream(out, true, Charsets.UTF_8),
            err = PrintStream(err, true, Charsets.UTF_8),
            repo = tmp.root.toPath(),
            installTraceProcessor = { error("download failed") },
            metricsSource = { source },
        )

        assertEquals(1, failing.run(listOf(traces.path)))
        assertTrue(err.toString(Charsets.UTF_8).contains("analyze: download failed"))
    }

    @Test
    fun `a directory with no traces is an error, and no trace processor is installed or started`() {
        assertEquals(1, command.run(listOf(traces.path)))
        assertTrue(err.toString(Charsets.UTF_8).contains("no .perfetto-trace files in ${traces.path}"))
        assertEquals(0, installs)
        assertEquals(null, source.shell)
    }

    @Test
    fun `a trace that can't be read is reported, and the trace processor is still stopped`() {
        File(traces, "iter000.perfetto-trace").writeText("")
        source.failure = IllegalStateException("broken trace")

        assertEquals(1, command.run(listOf("--trace-processor", shell.path, traces.path)))
        assertTrue(err.toString(Charsets.UTF_8).contains("analyze: broken trace"))
        assertTrue(source.closed)
    }

    @Test
    fun `a trace processor that isn't executable is a usage error`() {
        File(traces, "iter000.perfetto-trace").writeText("")
        val notExecutable = tmp.newFile("not_executable")

        assertEquals(2, command.run(listOf("--trace-processor", notExecutable.path, traces.path)))
        assertTrue(err.toString(Charsets.UTF_8).contains("is not an executable trace_processor_shell"))
    }

    @Test
    fun `arguments the command can't run with are usage errors that name the problem`() {
        assertUsageError(listOf("--trace-processor"), "--trace-processor needs a value")
        assertUsageError(listOf("--trace-processor", shell.path), "expected one traces directory, got 0")
        assertUsageError(listOf("--trace-processor", shell.path, "a", "b"), "expected one traces directory, got 2")
        assertUsageError(listOf("--trace-processor", shell.path, "--bogus", traces.path), "unknown option --bogus")
    }

    @Test
    fun `help prints the usage and succeeds`() {
        assertEquals(0, command.run(listOf(traces.path, "--help")))
        assertTrue(out.toString(Charsets.UTF_8).startsWith("usage: startup-tools analyze"))
    }

    private fun assertUsageError(args: List<String>, message: String) {
        err.reset()
        assertEquals(2, command.run(args))
        assertTrue(err.toString(Charsets.UTF_8).startsWith("analyze: $message\nusage: startup-tools analyze"))
    }

    /** Records what it was asked to read, and gives every trace the same metrics or [failure]. */
    private class FakeMetricsSource : StartupMetricsSource {
        var shell: Path? = null
        var failure: IllegalStateException? = null
        val read = mutableListOf<IterationTrace>()
        var closed = false

        override fun read(trace: IterationTrace): StartupMetrics {
            failure?.let { throw it }
            read.add(trace)
            return StartupMetrics(
                sections = mapOf("emb-modules-init" to 7.0, "emb-other-init" to 1.0),
                window = StartupWindow(
                    durationMs = 10.0,
                    source = StartupWindow.Source.SDK_START,
                    initThread = StartupWindow.ThreadScheduling(runningMs = 9.0, waitMs = 0.5, cpus = 2),
                ),
                ttidMs = 300.0,
            )
        }

        override fun close() {
            closed = true
        }
    }
}
