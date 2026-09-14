package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.campaign.Cohorts
import io.embrace.analysis.common.json.StartupJson
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files

/**
 * `cohorts`: classify every launch in a pass's `EmbVerify` logcat capture as created / restored /
 * unknown and report violations of the arm's expected cohort.
 */
class CohortsCommand : CliktCommand(name = "cohorts") {

    private val log by argument("log", help = "threadtime logcat capture filtered to EmbVerify:I").path(mustExist = true)
    private val method by option("--method", help = "StartupBenchmarks method, to derive the expected cohort")
    private val json by option("--json", help = "write the per-launch dataset here").path()

    override fun help(context: Context): String =
        "Verify which user-session path each launch of a pass took (created vs restored) from the sdk-init spans the " +
            "ExampleApp mirrored to logcat; exit 1 when any launch contradicts the arm."

    override fun run() {
        val report = Cohorts.report(Files.readString(log), method)
        report.launches.forEach { echo(Cohorts.launchLine(it)) }
        echo(report.summary)
        json?.let { Files.writeString(it, StartupJson.encodeToString(JsonObject.serializer(), Cohorts.toJson(report, method))) }
        if (report.violations.isNotEmpty()) throw ProgramResult(1)
    }
}
