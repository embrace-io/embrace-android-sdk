package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.analysis.StartupAnalysis
import io.embrace.startup.core.repo.RepoRoot
import io.embrace.startup.perfetto.Prebuilt
import io.embrace.startup.perfetto.TraceProcessor
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * `analyze` - the former `analyze_startup.py`. Runs `startup_metrics.sql` against every iteration
 * trace in a directory and prints the standard startup report; the same text is also written to a
 * uniquely named `startup-analysis-<timestamp>.txt` so successive runs never clobber each other.
 */
class AnalyzeCommand : CliktCommand(name = "analyze") {

    private val tracesDir by argument("traces-dir", help = "directory containing *.perfetto-trace files")
        .path(mustExist = true, canBeFile = false)
    private val traceProcessor by option(
        "--trace-processor",
        help = "path to a native trace_processor_shell (default: the pinned ${Prebuilt.VERSION} prebuilt, fetched once per machine)",
    ).path(mustExist = true)
    private val allSections by option("--all-sections", help = "list every emb-* section (default: canonical + top 15 others)")
        .flag()
    private val outputDir by option(
        "--output-dir",
        help = "directory for the timestamped summary file (default: <repo root>/claude-output)",
    )
        .path()
    private val repo by option(
        "--repo",
        help = "SDK repo root; defaults to git rev-parse --show-toplevel, else the nearest ancestor with .git",
    )
        .path()

    override fun help(context: Context): String =
        "Aggregate SDK startup metrics purely from macrobenchmark .perfetto-trace files: window, TTID, " +
            "canonical section durations and share of window, and per-iteration scheduler contention."

    override fun run() {
        val start = LocalDateTime.now()
        val outDir = outputDir ?: RepoRoot.claudeOutput(repo ?: RepoRoot.locate())
        val traces = StartupAnalysis.listTraces(tracesDir)
        if (traces.isEmpty()) {
            echo("no .perfetto-trace files in $tracesDir", err = true)
            throw ProgramResult(1)
        }
        val tp = TraceProcessor(Prebuilt.resolve(explicit = traceProcessor, repoRoot = repo))
        val perTrace = traces.map { it.fileName.toString() to StartupAnalysis.extract(tp, it) }

        val text = buildString {
            append("startup analysis started ${start.format(HEADER_TIME)}\n")
            append("traces dir: ${tracesDir.toAbsolutePath()}\n")
            append(StartupAnalysis.report(perTrace, allSections))
        }
        echo(text, trailingNewline = false)

        Files.createDirectories(outDir)
        val outPath = outDir.resolve("startup-analysis-${start.format(FILE_TIME)}.txt")
        Files.writeString(outPath, text)
        echo("\nsummary written to $outPath")
    }

    private companion object {
        val HEADER_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    }
}
