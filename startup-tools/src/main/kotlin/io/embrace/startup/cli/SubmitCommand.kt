package io.embrace.startup.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.startup.device.Adb
import io.embrace.startup.device.DeviceProvenance
import io.embrace.startup.store.Submit
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** `submit`: a locally ingested run into the shared corpus, redacted and validated. */
class SubmitCommand : CliktCommand(name = "submit") {

    private val store by option("--store", help = "local store.jsonl").path(mustExist = true).required()
    private val runId by option("--run-id").required()
    private val corpus by option("--corpus", help = "corpus.jsonl to append to").path().required()
    private val contributor by option("--contributor", help = "team or handle").required()
    private val serial by option("--serial", help = "used only to derive a local salted unit hash; never stored")
    private val lossyTolerance by option("--lossy-tolerance", help = "max share of lossy traces still admissible (default: none)")
        .double().default(0.0)
    private val dryRun by option("--dry-run").flag()

    override fun help(context: Context): String =
        "Turn a locally ingested run into a corpus submission: collect coarse device provenance, redact, validate " +
            "admissibility, append. Allowlist, not denylist; reject loudly."

    override fun run() {
        corpus.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        val local = Submit.findLocal(store, runId)
        if (local == null) {
            echo("run_id '$runId' not found in $store", err = true)
            throw ProgramResult(1)
        }
        val device = serial?.let { DeviceProvenance(Adb()).collect(it, corpus) }
        val outcome = Submit.build(local, contributor, runId, LocalDateTime.now().format(TIME), device, lossyTolerance)
        outcome.problems.forEach { echo("INADMISSIBLE: $it") }
        val record = outcome.record
        if (record == null) {
            echo(
                "\nsubmission refused - fix the above; a record that cannot be compared later is worse than no record",
                err = true,
            )
            throw ProgramResult(1)
        }
        echo(Submit.preview(record))
        if (dryRun) {
            echo("dry-run: nothing appended")
            return
        }
        Submit.append(corpus, record)
        echo("appended to $corpus")
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
