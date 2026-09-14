package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.device.Adb
import io.embrace.analysis.device.Topology
import io.embrace.analysis.device.TopologyProbe
import kotlinx.serialization.json.Json
import java.nio.file.Files

/** `probe`: one device's topology profile, written as `<name>-topology.json`. */
class ProbeCommand : CliktCommand(name = "probe") {

    private val serial by argument("serial")
    private val name by argument("name", help = "your label for this device; names the output file")
    private val outputDir by argument("output-dir").path(canBeFile = false)

    override fun help(context: Context): String =
        "Probe one connected device and write its DEVICE PROFILE (API, SoC, cluster map and little cpus, RAM and " +
            "storage class, tier, thermal sensors). Run FIRST for every device in a campaign and keep the JSON with the output."

    override fun run() {
        val topology = TopologyProbe(Adb()).probe(serial, name)
        Files.createDirectories(outputDir)
        val outPath = outputDir.resolve("$name-topology.json")
        Files.writeString(outPath, PRETTY.encodeToString(Topology.serializer(), topology))
        echo(topology.summary(outPath.toString()))
        val api = topology.apiLevel
        if (api != null && api < Topology.TRACING_API_FLOOR) {
            echo(
                "WARNING: API $api is below the API ${Topology.TRACING_API_FLOOR} floor for " +
                    "profileable shell tracing — only debuggable targets run here and their " +
                    "numbers are not comparable. Exclude this device or treat it as debug-only.",
                err = true,
            )
        }
        echo(
            "Record this profile with the campaign output; report tier / vendor / api_level " +
                "coverage for the whole device set before drawing cross-device conclusions.",
        )
    }

    private companion object {
        val PRETTY = Json(StartupJson) { prettyPrint = true }
    }
}
