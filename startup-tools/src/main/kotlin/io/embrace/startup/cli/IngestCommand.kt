package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.core.json.ReferenceSet
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.perfetto.Prebuilt
import io.embrace.startup.perfetto.TraceProcessor
import io.embrace.startup.store.Ingest
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** `ingest`: one run directory into the longitudinal store, with validation. */
class IngestCommand : CliktCommand(name = "ingest") {

    private val runDir by argument("run-dir").path(mustExist = true, canBeFile = false)
    private val referenceSet by option("--reference-set").path(mustExist = true).required()
    private val store by option("--store", help = "the JSONL store to append to").path().required()
    private val deviceKey by option("--device-key", help = "override when provenance cannot identify the device")
    private val traceProcessor by option("--trace-processor", help = "path to a native trace_processor_shell").path(mustExist = true)
    private val lossyTolerance by option(
        "--lossy-tolerance",
        help = "max % of traces allowed to report buffer-level loss before the run is refused (default 2%); their " +
            "windows are still used, since a lossy trace whose canary survived has a valid window",
    ).double().default(Ingest.DEFAULT_LOSSY_TOLERANCE_PCT)
    private val dryRun by option("--dry-run").flag()
    private val force by option("--force", help = "store despite validation failures; the reasons are recorded").flag()
    private val notBaseline by option("--not-baseline", help = "store as comparison-only even if the version looks published").flag()

    override fun help(context: Context): String =
        "Ingest one completed run into the longitudinal store, refusing runs whose device, profile, recipe or shape " +
            "does not match the reference set (or stamping the reasons in with --force)."

    override fun run() {
        val ref = StartupJson.decodeFromString(ReferenceSet.serializer(), Files.readString(referenceSet))
        val instrument = ref.recipe.instrument
        val provenance = Ingest.loadProvenance(runDir)
        val traces = Ingest.listTraces(runDir)
        val tpPath = if (instrument != null && traces.isNotEmpty()) Prebuilt.resolve(explicit = traceProcessor) else null
        val measurements = if (tpPath != null && instrument != null) {
            Ingest.measure(TraceProcessor(tpPath), traces, instrument)
        } else {
            emptyList()
        }
        val outcome = Ingest.build(
            runDir = runDir,
            ref = ref,
            provenance = provenance,
            measurements = measurements,
            tpPath = tpPath,
            options = Ingest.Options(deviceKey, lossyTolerance, force, notBaseline),
            ingestedAt = LocalDateTime.now().format(TIME),
        )
        outcome.refusedOutright?.let {
            echo(it, err = true)
            throw ProgramResult(1)
        }
        outcome.notes.forEach { echo(it) }
        outcome.problems.forEach { echo("VALIDATION: $it") }
        val record = outcome.record
        if (record == null) {
            echo("\nREFUSED: this run produced no usable windows; --force cannot store a placeholder.", err = true)
            throw ProgramResult(1)
        }
        if (outcome.problems.isNotEmpty() && !force) {
            echo(
                "\nREFUSED: fix the above, or re-run with --force to store it with these reasons " +
                    "recorded. A record that cannot be compared later is worse than no record.",
                err = true,
            )
            throw ProgramResult(1)
        }
        echo(Ingest.summaryLine(record))
        if (dryRun) {
            echo("dry-run: nothing written")
            return
        }
        Ingest.append(store, record)
        echo("appended to $store")
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
