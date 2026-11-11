package io.embrace.analysis.cli

import io.embrace.analysis.benchmark.IterationTraceFactory
import io.embrace.analysis.perfetto.TraceProcessorShell
import io.embrace.analysis.startup.AnalyzedIteration
import io.embrace.analysis.startup.StartupMetricsSource
import io.embrace.analysis.startup.StartupReport
import io.embrace.analysis.startup.TraceProcessorStartupMetricsSource
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * `analyze`: aggregates SDK startup metrics from every iteration trace in a directory, prints the report to [out], and
 * writes it to `startup-analysis-<start time>.txt` in the output directory, by default `claude-output` in [repo]. Traces
 * are read through a [StartupMetricsSource] that [metricsSource] makes from a trace_processor_shell binary: the one
 * `--trace-processor` names, or else the pinned one [installTraceProcessor] provides.
 */
class AnalyzeCommand(
    private val out: PrintStream,
    private val err: PrintStream,
    private val repo: Path,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val installTraceProcessor: () -> Path = { TraceProcessorShell(log = err).install() },
    private val metricsSource: (Path) -> StartupMetricsSource = ::TraceProcessorStartupMetricsSource,
) : Command {

    override val name: String = "analyze"

    override val summary: String = "aggregate SDK startup metrics from a directory of macrobenchmark traces"

    private val traceFactory = IterationTraceFactory()

    /** Returns 0, 1 when there is nothing to analyze or a trace can't be read, or 2 for a usage error. */
    override fun run(args: List<String>): Int {
        val options = try {
            Options.parse(args)
        } catch (e: UsageException) {
            err.println("$name: ${e.message}")
            err.print(USAGE)
            return 2
        }
        if (options == null) {
            out.print(USAGE)
            return 0
        }
        val chosen = options.traceProcessor
        if (chosen != null && (!Files.isRegularFile(chosen) || !Files.isExecutable(chosen))) {
            err.println("$name: $chosen is not an executable trace_processor_shell")
            return 2
        }
        return try {
            analyze(options)
        } catch (e: IllegalStateException) {
            err.println("$name: ${e.message}")
            1
        } catch (e: IOException) {
            err.println("$name: ${e.message}")
            1
        }
    }

    private fun analyze(options: Options): Int {
        val start = LocalDateTime.now(clock)
        if (!Files.isDirectory(options.tracesDir)) {
            err.println("$name: ${options.tracesDir} is not a directory")
            return 1
        }
        val traces = traceFactory.listIn(options.tracesDir)
        if (traces.isEmpty()) {
            err.println("no ${IterationTraceFactory.EXTENSION} files in ${options.tracesDir}")
            return 1
        }
        val shell = options.traceProcessor ?: installTraceProcessor()
        val iterations = metricsSource(shell).use { source ->
            traces.map { AnalyzedIteration(it, source.read(it)) }
        }

        val text = "startup analysis started ${start.format(HEADER_TIME)}\n" +
            "traces dir: ${options.tracesDir.toAbsolutePath().normalize()}\n" +
            StartupReport(iterations, options.allSections).render()
        out.print(text)

        val outDir = options.outputDir ?: repo.resolve("claude-output")
        Files.createDirectories(outDir)
        val summary = outDir.resolve("startup-analysis-${start.format(FILE_TIME)}.txt")
        Files.writeString(summary, text)
        out.println()
        out.println("summary written to $summary")
        return 0
    }

    /** What `analyze` runs with; [traceProcessor] is null for the pinned one. */
    private class Options(
        val tracesDir: Path,
        val traceProcessor: Path?,
        val allSections: Boolean,
        val outputDir: Path?,
    ) {
        companion object {

            /**
             * The options in [args], or null when they ask for the help text. Options take their value as the next
             * argument or after `=`. Throws [UsageException] for an unknown option, a missing value, or anything but
             * exactly one traces directory.
             */
            fun parse(args: List<String>): Options? {
                var traceProcessor: String? = null
                var outputDir: String? = null
                var allSections = false
                val positional = mutableListOf<String>()
                val remaining = args.iterator()
                while (remaining.hasNext()) {
                    val arg = remaining.next()
                    val option = arg.substringBefore('=')
                    val inlineValue = arg.substringAfter('=', missingDelimiterValue = "").takeIf { '=' in arg }
                    when (option) {
                        "-h", "--help" -> return null
                        "--all-sections" -> allSections = true
                        "--trace-processor", "--output-dir" -> {
                            val value = inlineValue ?: remaining.nextOrNull() ?: usageError("$option needs a value")
                            if (option == "--trace-processor") {
                                traceProcessor = value
                            } else {
                                outputDir = value
                            }
                        }
                        else -> {
                            if (arg.startsWith("-")) {
                                usageError("unknown option $arg")
                            }
                            positional.add(arg)
                        }
                    }
                }
                if (positional.size != 1) {
                    usageError("expected one traces directory, got ${positional.size}")
                }
                return Options(
                    tracesDir = Paths.get(positional.single()),
                    traceProcessor = traceProcessor?.let { Paths.get(it) },
                    allSections = allSections,
                    outputDir = outputDir?.let { Paths.get(it) },
                )
            }

            private fun Iterator<String>.nextOrNull(): String? = if (hasNext()) {
                next()
            } else {
                null
            }

            private fun usageError(message: String): Nothing = throw UsageException(message)
        }
    }

    /** Arguments `analyze` can't run with; the message says why. */
    private class UsageException(message: String) : IllegalArgumentException(message)

    private companion object {
        val HEADER_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

        val USAGE = """
            |usage: startup-tools analyze [--trace-processor <trace_processor_shell>] [--all-sections] [--output-dir <dir>] <traces-dir>
            |
            |Aggregates SDK startup metrics from the macrobenchmark .perfetto-trace files in <traces-dir>: each canonical
            |section's duration and share of the SDK-init window (the emb-sdk-start slice), TTID, the other emb-* sections, and
            |each iteration's main-thread scheduling inside the window. The report is printed and written to
            |claude-output/startup-analysis-<YYYY-MM-DD-HHMMSS>.txt (the analysis start time) in this repository.
            |
            |  --trace-processor <path>  a trace_processor_shell binary to use instead of the release the version catalog
            |                            pins (perfettoTraceProcessor), which is downloaded on first use
            |  --all-sections            list every other emb-* section, not only the 15 with the largest medians
            |  --output-dir <dir>        where to write the summary file, instead of claude-output
            |
            |Older SDKs (.claude/skills/startup-analysis/references/section-history.md): a trace without emb-sdk-start gets a
            |window composed from the first modules-init start to the first post-services-setup end, and a section renamed since
            |fills its current row, labelled with its old name.
            |
        """.trimMargin()
    }
}
