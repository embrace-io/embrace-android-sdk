package io.embrace.analysis.campaign

import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.common.proc.Processes
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Applies / reverts the per-version app-side compatibility patches needed to build
 * ExampleApp against old Embrace SDKs, and sets the version pin.
 *
 * Recipes are DATA and came from an actual sweep back to 6.14.0 on a modern AGP toolchain - re-verify
 * them when the app's AGP/Gradle/Kotlin move. Every patched file is snapshotted to a journal so
 * [revertAll] restores the tree even if a run dies mid-cell. Keep patches minimal: every extra edit is
 * a difference between arms that is NOT the SDK.
 */
class CompatPatch(
    private val repo: Path,
    private val gradle: (List<String>, Path) -> Processes.Output = { cmd, cwd -> Processes.run(cmd, cwd) },
) {

    data class Recipe(val name: String, val appliesTo: String, val pluginId: String?, val extraDeps: List<String>, val notes: String)

    /**
     * [ownerPid] and [appliedAt] exist so a patched tree can say who left it that way. The patch outlives
     * the process that applied it by design (apply, build, revert are separate commands), so a run that
     * dies in the middle leaves every later build silently resolving the wrong SDK. Recording the owner
     * lets [status] and [apply] say that out loud instead.
     */
    @Serializable
    data class Journal(
        val files: MutableMap<String, String> = LinkedHashMap(),
        var version: String? = null,
        var ownerPid: Long? = null,
        var appliedAt: String? = null,
    )

    class PatchError(message: String) : IllegalStateException(message)

    val example: Path = repo.resolve("examples/ExampleApp")
    val catalog: Path = example.resolve("gradle/libs.versions.toml")
    val journalFile: Path = example.resolve(".vfm-compat-journal.json")
    private val appBuild: Path = example.resolve("app/build.gradle.kts")

    fun readJournal(): Journal =
        if (Files.exists(journalFile)) StartupJson.decodeFromString(Journal.serializer(), Files.readString(journalFile)) else Journal()

    private fun writeJournal(journal: Journal) {
        Files.writeString(journalFile, StartupJson.encodeToString(Journal.serializer(), journal))
    }

    private fun snapshot(path: Path, journal: Journal) {
        val key = repo.relativize(path).toString()
        if (key !in journal.files) journal.files[key] = Files.readString(path)
    }

    /** The version the working tree publishes, from `gradle.properties`' `version=`. */
    fun localVersion(): String =
        Files.readAllLines(repo.resolve("gradle.properties")).firstOrNull { it.startsWith("version=") }
            ?.substringAfter("=")?.trim()
            ?: throw PatchError("gradle.properties has no version= line")

    /** Rewrite the catalog's `embrace = "..."` pin; returns the concrete version written. */
    fun setPin(version: String, journal: Journal): String {
        snapshot(catalog, journal)
        val text = Files.readString(catalog)
        val resolved = if (version == "local") localVersion() else version
        val replaced = PIN.replace(text) { "embrace = \"$resolved\"" }
        if (replaced == text && "\"$resolved\"" !in text) throw PatchError("could not rewrite the embrace pin - inspect the catalog format")
        Files.writeString(catalog, replaced)
        return resolved
    }

    /** Apply the recipe for [version]; returns the console lines to print. */
    fun apply(version: String): List<String> {
        val journal = readJournal()
        val lines = ArrayList<String>()
        lines.addAll(staleWarning(journal))
        journal.ownerPid = ProcessHandle.current().pid()
        journal.appliedAt = LocalDateTime.now().format(STAMP)
        val recipe = RECIPES.getValue(recipeFor(version))
        lines.add("recipe ${recipe.name} for $version: ${recipe.notes}")
        val resolved = setPin(version, journal)
        journal.version = version
        if (recipe.pluginId != MODERN_PLUGIN) {
            snapshot(appBuild, journal)
            var text = Files.readString(appBuild)
            text = if (recipe.pluginId == null) {
                PLUGIN_ANY.replace(text) { "    // [vfm] plugin intentionally omitted for 6.x plugin-less build" }
            } else {
                PLUGIN_MODERN.replace(text) { "    id(\"${recipe.pluginId}\")" }
            }
            recipe.extraDeps.forEach { template ->
                val dep = template.replace("{version}", resolved)
                if (dep.split(":")[1] !in text) {
                    text = text.replaceFirst("dependencies {", "dependencies {\n    implementation(\"$dep\")")
                }
            }
            Files.writeString(appBuild, text)
            lines.add("patched ${repo.relativize(appBuild)}")
        }
        writeJournal(journal)
        lines.add("pin set to $resolved; journal has ${journal.files.size} snapshot(s)")
        if (recipe.pluginId == null) {
            lines.add(
                "REMINDER: 6.x needs hand-injected config resources + exporter setup; see " +
                    "references/version-compat.md before trusting the build",
            )
        }
        return lines
    }

    /**
     * Whether this checkout is currently patched, and by whom. Cheap to call and safe to run any time:
     * the question "why is my build resolving an old SDK" has no other answer short of reading a diff.
     */
    fun status(): List<String> {
        val journal = readJournal()
        if (journal.files.isEmpty()) {
            return listOf("clean: no compat patch applied")
        }
        val owner = journal.ownerPid
        val alive = owner != null && ProcessHandle.of(owner).map { it.isAlive }.orElse(false)
        return listOf(
            "PATCHED for ${journal.version ?: "an unrecorded version"}: " +
                "${journal.files.size} file(s) rewritten, applied ${journal.appliedAt ?: "at an unrecorded time"}" +
                (owner?.let { " by pid $it${if (alive) " (still running)" else " (no longer running)"}" } ?: ""),
            "every build in this checkout resolves the patched version until `compat-patch --revert-all`",
        ) + journal.files.keys.map { "  patched: $it" }
    }

    /** Said at the top of [apply] when the tree was already patched, most likely by a run that died. */
    private fun staleWarning(journal: Journal): List<String> {
        if (journal.files.isEmpty()) return emptyList()
        val owner = journal.ownerPid
        val alive = owner != null && ProcessHandle.of(owner).map { it.isAlive }.orElse(false)
        if (alive) {
            return listOf("NOTE: pid $owner is patching this checkout right now; two patchers will fight over the same files")
        }
        return listOf(
            "WARNING: this checkout was already patched for ${journal.version ?: "an unrecorded version"} " +
                "(applied ${journal.appliedAt ?: "at an unrecorded time"}) and never reverted - a previous run died.",
            "  The ORIGINAL files are still in the journal, so `compat-patch --revert-all` restores them.",
        )
    }

    fun revertAll(): List<String> {
        val journal = readJournal()
        if (journal.files.isEmpty()) return listOf("nothing to revert")
        val lines = ArrayList<String>()
        journal.files.forEach { (rel, content) ->
            Files.writeString(repo.resolve(rel), content)
            lines.add("reverted $rel")
        }
        Files.deleteIfExists(journalFile)
        lines.add("journal cleared - now confirm `git status --short` shows only intended changes")
        return lines
    }

    /** `:app:assembleBenchmark -q` on the patched tree; the tails of both streams on failure. */
    fun verifyBuild(): Pair<Boolean, List<String>> {
        val lines = arrayListOf("compile-checking the patched tree (assembleBenchmark)...")
        val out =
            gradle(listOf(example.resolve("gradlew").toString(), "-p", example.toString(), ":app:assembleBenchmark", "-q"), example)
        if (out.exitCode != 0) {
            lines.add(out.stdout.takeLast(TAIL_CHARS))
            lines.add(out.stderr.takeLast(TAIL_CHARS))
            lines.add("VERIFY FAILED: patched tree does not build - fix the recipe before running cells")
            return false to lines
        }
        lines.add("verify OK: patched tree builds")
        return true to lines
    }

    companion object {
        const val MODERN_PLUGIN: String = "io.embrace.gradle"

        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        val RECIPES: Map<String, Recipe> = listOf(
            Recipe("modern", ">=8.0", MODERN_PLUGIN, emptyList(), "no patch needed"),
            Recipe(
                "7x",
                "7.0-7.9.x",
                "io.embrace.swazzler",
                listOf("io.embrace:embrace-android-fcm:{version}"),
                "swazzler plugin DOES apply on modern AGP; embrace-android-fcm must be declared " +
                    "explicitly because later versions bundle it transitively",
            ),
            Recipe(
                "6x",
                "<=6.14.x",
                null,
                emptyList(),
                "build WITHOUT the plugin: hand-inject config resources and satisfy the no-appId " +
                    "path with exporters. Mildly favourable to 6.x - label it in the report",
            ),
        ).associateBy { it.name }

        fun recipeFor(version: String): String {
            if (version == "local") return "modern"
            val major = version.substringBefore(".").toIntOrNull() ?: return "modern"
            return when {
                major >= MODERN_MAJOR -> "modern"
                major == SEVEN -> "7x"
                else -> "6x"
            }
        }

        private val PIN = Regex("^embrace = \".*\"$", RegexOption.MULTILINE)
        private val PLUGIN_ANY = Regex(
            "^\\s*(alias\\(libs\\.plugins\\.embrace\\)|id\\(\"io\\.embrace\\.[^\"]+\"\\)).*$",
            RegexOption.MULTILINE,
        )
        private val PLUGIN_MODERN = Regex(
            "^\\s*(alias\\(libs\\.plugins\\.embrace\\)|id\\(\"io\\.embrace\\.gradle\"\\)).*$",
            RegexOption.MULTILINE,
        )
        private const val MODERN_MAJOR = 8
        private const val SEVEN = 7
        private const val TAIL_CHARS = 2000
    }
}
