package io.embrace.analysis.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.embrace.analysis.common.repo.RepoRoot
import io.embrace.analysis.records.store.ArtifactManifest

/**
 * `artifact-sync`: guard against local/published drift in the living docs. Run `check` BEFORE
 * editing, `record` AFTER publishing. The manifest is
 * `artifact-manifest.json` in the committed records root (`RecordsRoot.dir()`), beside the document
 * sources it describes.
 */
class ArtifactSyncCommand : CliktCommand(name = "artifact-sync") {
    init {
        subcommands(Check(), Record(), ListAll())
    }

    override fun help(context: Context): String =
        "Living-doc drift guard: check a local HTML before editing, record it after publishing, list every tracked doc. " +
            "Always reconcile before updating; the PUBLISHED copy is the truth unless you know you changed the local one."

    override fun run() = Unit

    private class Check : CliktCommand(name = "check") {
        private val file by argument("local.html").path()
        private val repo by option("--repo").path()

        override fun help(context: Context): String = "May I edit this yet? Prints UNKNOWN / CLEAN / DIRTY with the reasoning."

        override fun run() {
            val (code, text) = ArtifactManifest(repo ?: RepoRoot.locate()).check(file)
            echo(text)
            if (code != 0) throw ProgramResult(code)
        }
    }

    private class Record : CliktCommand(name = "record") {
        private val file by argument("local.html").path(mustExist = true)
        private val url by argument("artifact-url")
        private val stamp by argument("published-at", help = "pass the publish time rather than letting this invent one").optional()
        private val repo by option("--repo").path()

        override fun help(context: Context): String = "Record the digest of a just-published file against its artifact URL."

        override fun run() {
            echo(ArtifactManifest(repo ?: RepoRoot.locate()).record(file, url, stamp ?: "unrecorded"))
        }
    }

    private class ListAll : CliktCommand(name = "list") {
        private val repo by option("--repo").path()

        override fun help(context: Context): String = "Every tracked doc and its state."

        override fun run() {
            echo(ArtifactManifest(repo ?: RepoRoot.locate()).list())
        }
    }
}
