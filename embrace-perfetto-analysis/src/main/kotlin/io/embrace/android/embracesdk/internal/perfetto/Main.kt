package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.cli.CliOptions
import io.embrace.android.embracesdk.internal.perfetto.cli.CliSpec
import io.embrace.android.embracesdk.internal.perfetto.cli.asksForHelp
import io.embrace.android.embracesdk.internal.perfetto.cli.parseArgs
import io.embrace.android.embracesdk.internal.perfetto.model.TraceInterpreter
import io.embrace.android.embracesdk.internal.perfetto.proto.Trace
import io.embrace.android.embracesdk.internal.perfetto.report.render
import io.embrace.android.embracesdk.internal.perfetto.stats.StatsReport
import io.embrace.android.embracesdk.internal.perfetto.stats.calculateStats
import io.embrace.android.embracesdk.internal.perfetto.trace.TraceFormat
import io.embrace.android.embracesdk.internal.perfetto.trace.ftraceEvents
import io.embrace.android.embracesdk.internal.perfetto.trace.parseTrace
import io.embrace.android.embracesdk.internal.perfetto.trace.printEvents
import io.embrace.android.embracesdk.internal.perfetto.trace.threadNames
import io.embrace.android.embracesdk.internal.perfetto.trace.traceStartNanos
import io.embrace.android.embracesdk.internal.perfetto.trace.traceWindowNanos
import io.embrace.android.embracesdk.internal.perfetto.trace.validateTrace
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

private val SPEC = CliSpec(
    command = "analyseTrace",
    inputs = listOf("<trace.perfetto.gz>"),
    notes = "Normally run via scripts/analyse-trace.sh.",
)

fun main(args: Array<String>) {
    if (asksForHelp(args)) {
        println(SPEC.usage)
        return
    }
    val options = parseArgs(SPEC, args)
    if (options == null) {
        System.err.println(SPEC.usage)
        exitProcess(EXIT_USAGE)
    }
    if (!options.input.isFile) {
        System.err.println("no trace file at ${options.input.absolutePath}")
        exitProcess(EXIT_BAD_TRACE)
    }
    val format = validateTrace(options.input)
    if (format != TraceFormat.PERFETTO) {
        System.err.println("${options.input.absolutePath} is ${format.label}")
        exitProcess(EXIT_BAD_TRACE)
    }
    println(describe(options, format))
    if (options.dryRun) {
        return
    }
    val trace = try {
        parseTrace(options.input)
    } catch (exc: IOException) {
        System.err.println(exc.message)
        exitProcess(EXIT_BAD_TRACE)
    }
    println(summarise(trace))
    try {
        println(writeStats(options, trace))
    } catch (exc: IOException) {
        System.err.println("could not write the report: ${exc.message}")
        exitProcess(EXIT_BAD_OUTPUT)
    }
}

/** Reports the inputs the analysis runs against. */
internal fun describe(options: CliOptions, format: TraceFormat): String = buildString {
    appendLine("perfetto trace analysis")
    appendLine("  trace: ${options.input.path} (${options.input.length()} bytes)")
    appendLine("  format: ${format.label}")
    append("  report: ${options.output.path} (${options.format.flag})")
}

internal fun summarise(trace: Trace): String = buildString {
    val events = ftraceEvents(trace)
    val prints = printEvents(events)
    val model = TraceInterpreter().interpret(prints, threadNames(trace))
    appendLine("  packets: ${trace.packet.size}")
    appendLine("  ftrace events: ${events.size}")
    // ftrace calls this `pid`, but it holds a thread id
    appendLine("  atrace events: ${prints.size} across ${model.threads.size} threads")
    appendLine("  slices: ${model.sliceCount} of ${model.names.size} distinct sections")
    model.threads.values.forEach { timeline ->
        val named = timeline.name?.let { " ($it)" }.orEmpty()
        appendLine("    tid ${timeline.tid}$named: ${timeline.slices.size} slices")
    }
    appendLine("  counters: ${model.counterSampleCount} samples of ${model.counterNames.size} distinct counters")
    append("  skipped: ${model.unclosed} unclosed, ${model.unopened} unopened, ${model.unsupported} unsupported")
}

internal fun writeStats(options: CliOptions, trace: Trace): String {
    options.output.writeText(render(options.format, statsReport(options, trace)))
    return "wrote ${options.format.flag} statistics to ${options.output.path}"
}

internal fun statsReport(options: CliOptions, trace: Trace): StatsReport =
    statsReport(options.input, trace, options.operations)

internal fun statsReport(file: File, trace: Trace, operations: List<String>): StatsReport {
    val events = ftraceEvents(trace)
    val model = TraceInterpreter().interpret(events, threadNames(trace))
    val window = traceWindowNanos(events)
    val requested = operations.ifEmpty { model.names.sorted() }
    return StatsReport(
        tracePath = file.path,
        traceSizeBytes = file.length(),
        sliceCount = model.sliceCount,
        sectionCount = model.names.size,
        threadCount = model.threads.size,
        traceWindowNanos = window,
        stats = calculateStats(model, requested, window, traceStartNanos(events)),
    )
}
