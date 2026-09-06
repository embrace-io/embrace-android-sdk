package io.embrace.startup.check

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

/**
 * Sweep the skills and the tooling for one author's personal setup. Ported from the production repo's
 * `check_leaks.py`, whose design this keeps: a checked-in layer of GENERIC patterns describing the
 * *shape* of a leak, and a git-ignored layer of PERSONAL tokens naming one author's actual values.
 *
 * Other engineers run these skills against their own devices, so nothing about one machine's runs
 * should ship in the repo: no home paths, no email addresses, no device serials, no links to a private
 * artifact, no citation of a dated run directory that only exists on one laptop.
 *
 * Two scopes, because evidence and instructions are held to different standards:
 *  - [Scope.SOURCE] is the skills' prose and the tool's code, where a run-specific citation is a defect:
 *    the reader has no such run. Every pattern applies.
 *  - [Scope.EVERYWHERE] also covers the committed records, where citing a real run is the whole point.
 *    Only the patterns that are personal rather than run-specific apply there.
 *
 * A line containing `leakcheck:allow` is skipped entirely, for the case that legitimately looks like a
 * leak. `--allow REGEX` suppresses one matched text for a single run without editing anything.
 */
object LeakCheck {

    enum class Scope { EVERYWHERE, SOURCE }

    /**
     * One leak shape, with the samples that prove it still works. The samples live on the pattern rather
     * than in a table beside it, so a pattern cannot be added or edited without its own test moving too.
     */
    class Leak(
        val name: String,
        val regex: Regex,
        val scope: Scope,
        /** Must match. */
        val positive: String,
        /** Must not match: the near miss this pattern has to stay clear of. */
        val negative: String,
    )

    data class Finding(val file: String, val line: Int, val pattern: String, val text: String) {
        override fun toString(): String = "$file:$line: $pattern: ${text.trim().take(TRUNCATE)}"
    }

    data class Report(val findings: List<Finding>, val filesChecked: Int, val localTokenFile: Path?)

    val PATTERNS: List<Leak> = listOf(
        Leak(
            "home-path",
            Regex("""/(Users|home)/[A-Za-z0-9._-]+/"""),
            Scope.EVERYWHERE,
            positive = "see /Users/alice/work/embrace-android-sdk",
            negative = "see /usr/local/lib",
        ),
        Leak(
            // The domain must start with a letter: an Android HAL service name is `pkg@2.0-service.vendor`,
            // which is the same shape as an address except that its "domain" starts with the version.
            "email-address",
            Regex("""[A-Za-z0-9._%+-]+@[A-Za-z][A-Za-z0-9.-]*\.[A-Za-z]{2,}"""),
            Scope.EVERYWHERE,
            positive = "reach me at foo.bar@example.com",
            negative = "android.hardware.thermal@2.0-service.pixel",
        ),
        Leak(
            // Android serials as this fleet's devices report them: upper-case alphanumeric, at least three
            // letters, and a letter somewhere after a digit. Underscored constants and lower-case digests
            // are excluded by the character class; the three-letter floor clears scientific notation like
            // `1329408E7` from the trace CSVs, and the letter-after-digit rule clears a name whose digits
            // are a suffix, such as `DECIMAL128`.
            "device-serial",
            // Every lookahead stays inside the token: `[0-9]*` and `[A-Z0-9]*` cannot cross a separator,
            // where `[^A-Z]*` would run on into the rest of the line and count its letters as this one's.
            Regex("""\b(?=[A-Z0-9]{9,17}\b)(?=(?:[0-9]*[A-Z]){3})(?=[A-Z0-9]*[0-9][A-Z0-9]*[A-Z])[A-Z0-9]+\b"""),
            Scope.EVERYWHERE,
            positive = "device R58W211D4ZD is the mid tier",
            negative = "rounding with DECIMAL128 here",
        ),
        Leak(
            "aws-access-key",
            Regex("""AKIA[0-9A-Z]{16}"""),
            Scope.EVERYWHERE,
            positive = "key AKIAABCDEFGHIJKLMNOP here",
            negative = "key AKIA123 here",
        ),
        Leak(
            "secret-assignment",
            Regex("""\b(password|passwd|secret|token|api[_-]?key)\b\s*[:=]\s*['"][^'"]{8,}""", RegexOption.IGNORE_CASE),
            Scope.EVERYWHERE,
            positive = """password = "hunter2222"""",
            negative = """val password: String? = null""",
        ),
        Leak(
            "scratchpad-path",
            Regex("""/private/tmp/claude-"""),
            Scope.EVERYWHERE,
            positive = "wrote /private/tmp/claude-501/foo",
            negative = "wrote /private/tmp/other-501/foo",
        ),
        Leak(
            "claude-artifact-url",
            Regex("""claude\.ai/code/artifact/"""),
            Scope.SOURCE,
            positive = "see https://claude.ai/code/artifact/abcd1234",
            negative = "see https://claude.ai/code/other",
        ),
        Leak(
            // The scratch directory itself is a documented part of the repo; one dated run inside it is
            // one machine's output, and citing it tells the reader to look somewhere they have nothing.
            "scratch-run-dir",
            Regex("""\bclaude-output/\d{4}-\d{2}"""),
            Scope.SOURCE,
            positive = "results in claude-output/2026-08-11-campaign",
            negative = "scratch lives in claude-output/",
        ),
        Leak(
            "dated-run-dir",
            Regex("""\b\d{4}-\d{2}-\d{2}-\d{4,6}\b"""),
            Scope.SOURCE,
            positive = "startup-analysis-2026-08-11-175238.txt",
            negative = "captured on 2026-08-11 by hand",
        ),
        Leak(
            // X-codes only. The tracker's P-codes are indistinguishable from the percentile labels
            // (`P90`, `P95`) that fill the reporting code, and a check that cries wolf gets ignored.
            "experiment-code",
            Regex("""\bX\d{1,2}[a-d]?\b"""),
            Scope.SOURCE,
            positive = "as X25 showed on the fleet",
            negative = "the P90 of the window",
        ),
    )

    /**
     * Scan [repo]. [extra] paths are scanned in addition to the default scope, whatever their extension.
     * [localTokens] are matched case-insensitively as plain substrings; [allow] suppresses a hit whose
     * matched text matches any of them.
     */
    fun scan(
        repo: Path,
        extra: List<Path> = emptyList(),
        allow: List<Regex> = emptyList(),
        localTokens: List<String> = emptyList(),
    ): Report {
        val findings = ArrayList<Finding>()
        var checked = 0
        val targets = LinkedHashMap<Path, Scope>()
        // The source pass walks past the records, which the data pass then covers under its own scope.
        SOURCE_ROOTS.forEach { rel -> collect(repo.resolve(rel), Scope.SOURCE, SOURCE_EXTENSIONS, targets, SKIP_IN_SOURCE) }
        DATA_ROOTS.forEach { rel -> collect(repo.resolve(rel), Scope.EVERYWHERE, DATA_EXTENSIONS, targets, SKIP_DIRECTORIES) }
        extra.forEach { collect(it, Scope.SOURCE, null, targets, SKIP_DIRECTORIES) }

        targets.forEach { (file, scope) ->
            checked++
            val rel = runCatching { repo.relativize(file.toAbsolutePath()).toString() }.getOrDefault(file.toString())
            if (file.fileName.toString().endsWith(".zip")) {
                findings += scanArchive(file, rel, scope, allow, localTokens)
            } else {
                findings += scanLines(Files.readAllLines(file, Charsets.UTF_8), rel, scope, allow, localTokens)
            }
        }
        val local = localTokenFile(repo)
        return Report(findings, checked, local)
    }

    /** The personal token list: one substring per line, `#` comments. Never committed. */
    fun loadLocalTokens(repo: Path): List<String> {
        val path = localTokenFile(repo) ?: return emptyList()
        return Files.readAllLines(path)
            .map { it.substringBefore("#").trim() }
            .filter { it.isNotEmpty() }
    }

    fun localTokenFile(repo: Path): Path? {
        System.getenv("LEAKCHECK_LOCAL")?.let { env ->
            val p = Path.of(env)
            if (Files.isRegularFile(p)) return p
        }
        return repo.resolve(LOCAL_FILE).takeIf { Files.isRegularFile(it) }
    }

    /** Every pattern against its own samples; returns the failures, empty when all hold. */
    fun selfTest(): List<String> = PATTERNS.flatMap { leak ->
        buildList {
            if (!leak.regex.containsMatchIn(leak.positive)) {
                add("${leak.name} did not match its positive sample: ${leak.positive}")
            }
            if (leak.regex.containsMatchIn(leak.negative)) {
                add("${leak.name} matched its negative sample: ${leak.negative}")
            }
        }
    }

    private fun scanLines(
        lines: List<String>,
        rel: String,
        scope: Scope,
        allow: List<Regex>,
        localTokens: List<String>,
    ): List<Finding> {
        val findings = ArrayList<Finding>()
        lines.forEachIndexed { index, line ->
            if (ALLOW_MARKER in line) return@forEachIndexed
            PATTERNS.forEach { leak ->
                if (leak.scope == Scope.SOURCE && scope != Scope.SOURCE) return@forEach
                leak.regex.findAll(line).forEach { match ->
                    if (allow.none { it.containsMatchIn(match.value) }) {
                        findings.add(Finding(rel, index + 1, leak.name, match.value))
                    }
                }
            }
            val lowered = line.lowercase()
            localTokens.forEach { token ->
                if (token.lowercase() in lowered && allow.none { it.containsMatchIn(token) }) {
                    findings.add(Finding(rel, index + 1, "local:$token", line))
                }
            }
        }
        return findings
    }

    /** The records keep evidence in archives, so a leak can hide inside one; read them without unpacking. */
    private fun scanArchive(
        archive: Path,
        rel: String,
        scope: Scope,
        allow: List<Regex>,
        localTokens: List<String>,
    ): List<Finding> {
        val findings = ArrayList<Finding>()
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val leaf = name.substringAfterLast('/')
                val exempt = leaf in EXEMPT_FILES || leaf.startsWith(EXEMPT_PREFIX)
                if (!entry.isDirectory && !exempt && DATA_EXTENSIONS.any { name.endsWith(it) }) {
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    findings += scanLines(text.split("\n"), "$rel!$name", scope, allow, localTokens)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return findings
    }

    private fun collect(
        root: Path,
        scope: Scope,
        extensions: Set<String>?,
        into: MutableMap<Path, Scope>,
        skip: Set<String>,
    ) {
        if (!Files.exists(root)) return
        if (Files.isRegularFile(root)) {
            into.putIfAbsent(root, scope)
            return
        }
        Files.walk(root).use { walk ->
            walk.filter { Files.isRegularFile(it) }.forEach { file ->
                val parts = file.map { it.toString() }
                if (parts.any { it in skip }) return@forEach
                val name = file.fileName.toString()
                if (name in EXEMPT_FILES || name.startsWith(EXEMPT_PREFIX)) return@forEach
                if (extensions == null || extensions.any { name.endsWith(it) }) {
                    into.putIfAbsent(file, scope)
                }
            }
        }
    }

    /** The skills' prose and the tool's own code: a run-specific citation here is a defect. */
    private val SOURCE_ROOTS = listOf(
        ".claude/skills",
        "startup-tools/src/main",
        "startup-tools/README.md",
        "tools",
    )

    /**
     * The committed evidence: citing a real run is its job, so only the personal patterns apply. The port
     * log belongs here rather than with the source - it is a record of what was decided and when, and its
     * entries cite the runs that decided them.
     */
    private val DATA_ROOTS = listOf(
        ".claude/skills/_shared/records",
        "startup-tools/src/test/resources/fixtures",
        "startup-tools/PORT-LOG.md",
    )

    private val SOURCE_EXTENSIONS = setOf(".md", ".kt", ".kts", ".sh", ".json", ".yaml", ".yml")
    private val DATA_EXTENSIONS = setOf(".md", ".txt", ".log", ".json", ".jsonl", ".csv", ".html", ".zip")

    private val SKIP_DIRECTORIES = setOf("build", ".gradle", ".git")
    private val SKIP_IN_SOURCE = SKIP_DIRECTORIES + "records"

    /**
     * Files whose job is to carry device provenance in machine-readable form: the serial-to-key map and
     * the run metadata a scorer resolves the device from. The rule the fleet works by is that a serial may
     * appear in provenance and never in prose, so these are exempt and every document is not. The checker
     * and its test hold a sample of every leak shape by construction, so they are the one place the
     * patterns must not read.
     */
    private val EXEMPT_FILES = setOf(
        "run-metadata.json",
        "cell-state.json",
        "corpus.jsonl",
        "reproducibility.json",
        "reproducibility.stdout.txt",
        "LeakCheck.kt",
        "LeakCheckTest.kt",
    )
    private const val EXEMPT_PREFIX = "reference-set"

    private const val LOCAL_FILE = ".leakcheck-local"
    private const val ALLOW_MARKER = "leakcheck:allow"
    private const val TRUNCATE = 80
}
