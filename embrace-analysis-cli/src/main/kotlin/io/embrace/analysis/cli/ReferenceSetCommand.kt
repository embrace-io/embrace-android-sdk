package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.device.Adb
import io.embrace.analysis.device.DeviceProbe
import io.embrace.analysis.records.store.DeviceSerials
import io.embrace.analysis.records.store.ReferenceSetTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** `reference-set`: probe, check for drift, or show the reference device set. */
class ReferenceSetCommand : CliktCommand(name = "reference-set") {

    private val probe by option("--probe", help = "probe every attached device's profile").flag()
    private val out by option("--out", help = "with --probe: write a new reference set here").path()
    private val check by option("--check", help = "with --probe: compare against this reference set, no writes").path(mustExist = true)
    private val show by option("--show", help = "print a reference set with its coverage gaps").path(mustExist = true)

    override fun help(context: Context): String =
        "Declare and inspect the stable reference device set: probe profiles, assign provisional keys, freeze the " +
            "recipe (instrument deliberately unset), and flag profile drift on re-probe."

    override fun run() {
        show?.let { path ->
            val doc = StartupJson.parseToJsonElement(Files.readString(path)).jsonObject
            echo(PRETTY.encodeToString(JsonObject.serializer(), doc))
            ReferenceSetTool.coverageWarnings(doc["devices"]?.jsonObject ?: JsonObject(emptyMap())).forEach {
                echo("COVERAGE GAP: $it")
            }
            return
        }
        if (!probe) {
            echo("use --probe (with --out or --check), or --show <file>", err = true)
            throw ProgramResult(1)
        }
        val adb = Adb()
        val serials = adb.attachedSerials()
        if (serials.isEmpty()) {
            echo("no attached devices (adb devices shows none in state 'device')", err = true)
            throw ProgramResult(1)
        }
        val probed = serials.associateWith { DeviceProbe(adb).profile(it) }
        val localSerials = DeviceSerials.load(DeviceSerials.file())
        check?.let { path ->
            // The committed set names devices by key; the check needs serials, so they come from the local map.
            val doc = StartupJson.parseToJsonElement(Files.readString(path)).jsonObject
            ReferenceSetTool.check(DeviceSerials.hydrate(doc, DeviceSerials.merge(localSerials, doc)), probed).forEach { echo(it) }
            return
        }
        val (declared, lines) = ReferenceSetTool.declare(probed, LocalDateTime.now().format(TIME))
        lines.forEach { echo(it) }
        // Serials never enter the committed document: they go to the machine-local map, and the file
        // written here names devices by key only.
        val (doc, declaredSerials) = DeviceSerials.split(declared)
        val text = PRETTY.encodeToString(JsonObject.serializer(), doc)
        val target = out
        if (target != null) {
            Files.writeString(target, text)
            val merged = DeviceSerials.Serials(localSerials.byKey + declaredSerials.byKey)
            DeviceSerials.save(merged, DeviceSerials.file())
            echo("\nwrote $target - review the keys and tiers before your first ingest")
            echo("serials written to ${DeviceSerials.file()} (local, never committed); rename a key in both files together")
        } else {
            echo(text)
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        /** One-space indent (`json.dumps(indent=1)` style), which the goldens pin for the reference set. */
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val PRETTY = Json(StartupJson) {
            prettyPrint = true
            prettyPrintIndent = " "
        }
    }
}
