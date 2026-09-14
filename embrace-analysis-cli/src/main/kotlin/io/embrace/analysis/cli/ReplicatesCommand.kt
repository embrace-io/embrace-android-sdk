package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.maxims.Replicates
import io.embrace.analysis.records.store.DeviceSerials
import io.embrace.analysis.records.store.RecordsRoot

/** `replicates`: between-campaign spread and verdict stability for every cell measured more than once. */
class ReplicatesCommand : CliktCommand(name = "replicates") {

    private val records by option("--records", help = "records root (default: ${RecordsRoot.REL} under the repo)")
        .path(mustExist = true, canBeFile = false)

    override fun help(context: Context): String =
        "For every cell (device key, SDK version, arm, method, shape) with more than one campaign: the spread of medians " +
            "and tails between campaigns against the within-campaign pass spread, and the maxims whose verdict flips."

    override fun run() = echo(
        Replicates.report(records ?: RecordsRoot.dir(), DeviceSerials.load(DeviceSerials.file())),
        trailingNewline = false,
    )
}
