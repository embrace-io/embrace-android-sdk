package io.embrace.startup.campaign

import io.embrace.startup.core.json.DeviceProfile
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.proc.Processes
import io.embrace.startup.core.text.PyFormat
import io.embrace.startup.device.Adb
import io.embrace.startup.device.DeviceProbe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.streams.toList

/**
 * `fleet_campaign.py`: N back-to-back benchmark passes on one device, each pass's traces copied
 * aside before the next pass wipes them, with battery AND silicon temperatures logged around every
 * pass and a silicon cool gate between passes.
 *
 * The cool gate is relative to the device's own SETTLED pre-campaign silicon temperature rather
 * than absolute, because idle baselines differ by tier and vendor. It gates on silicon, never on
 * battery: the entry-tier device failed its 9.0.0 leg twice with a battery-based gate reading "cool"
 * at 31 °C while the CPU sat at 55 °C. Best-effort - unreadable sensors proceed, loudly.
 *
 * Departure from the Python (recorded in the port log): a `run-metadata.json` is written into the
 * campaign directory (serial, method, declared shape, catalog pin, repo head) so `ingest` has
 * provenance; the Python left that file to the operator, and nothing in the toolchain wrote it.
 */
class FleetCampaign(
    private val serial: String,
    private val dirMatch: String,
    private val campDir: Path,
    private val passes: Int,
    private val method: String = "coldStartup",
    private val iterations: Int? = null,
    private val gapAfterPass: Int? = null,
    private val repo: Path,
    private val adb: Adb = Adb(),
    private val gradle: (List<String>, Path, Map<String, String>) -> Processes.Output = { cmd, cwd, env ->
        Processes.run(cmd, cwd, env)
    },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val dryRun: Boolean = false,
    /** Verify each launch's user-session cohort from the app's logcat tap (see [Cohorts]); off only for A/B against old runs. */
    private val verifyCohort: Boolean = true,
    /** Starts a background `adb ... logcat` writing to the file; the returned handle stops it. Injected so tests never spawn adb. */
    private val logcat: (List<String>, Path) -> AutoCloseable = { cmd, file -> Processes.background(cmd, file) },
) {

    private val app: Path = repo.resolve("examples/ExampleApp")
    private val connected: Path = app.resolve(CONNECTED_SUBPATH)

    fun run(): Int {
        if (!Files.isDirectory(app)) {
            System.err.println("$app does not exist — is $repo the right repo root?")
            return 1
        }
        Files.createDirectories(campDir)
        log("repo $repo")
        writeRunMetadata()
        if (dryRun) {
            log("dry-run: would run ${benchmarkCommand().joinToString(" ")} with ANDROID_SERIAL=$serial, cwd $repo")
            log("dry-run: traces would be read from $connected (hint '$dirMatch') into $campDir/passN")
            log("dry-run: battery ${Thermal.battery(adb, serial)}, ${Thermal.silicon(adb, serial)}")
            return 0
        }
        val baseline = settleBaseline()
        log("silicon baseline for the cool gate (settled): ${baseline?.let { PyFormat.fixed(it, 1) } ?: "None"}")
        val previousTap = if (verifyCohort) armCohortTap() else null
        try {
            for (p in 1..passes) {
                if (Files.isDirectory(campDir.resolve("pass$p"))) {
                    log("pass $p already collected, skipping")
                    continue
                }
                if (!runPass(p, baseline)) {
                    return 1
                }
            }
        } finally {
            if (verifyCohort) {
                disarmCohortTap(previousTap)
            }
        }
        log("$campDir complete")
        return 0
    }

    /**
     * Turn on the ExampleApp's logcat telemetry tap for the campaign and remember what it was, so the
     * device is left as found. The tap mirrors the startup spans (with every sdk-init attribute) to
     * logcat one second after init; it adds a span processor to the app under test, so every arm of a
     * comparison must run with it in the same state.
     */
    private fun armCohortTap(): String {
        val previous = adb.shell(serial, "settings", "get", "global", Cohorts.SETTING_KEY)
        adb.shell(serial, "settings", "put", "global", Cohorts.SETTING_KEY, Cohorts.SETTING_VALUE)
        log("cohort verification: logcat tap armed (${Cohorts.SETTING_KEY}=${Cohorts.SETTING_VALUE}; was '$previous')")
        return previous
    }

    private fun disarmCohortTap(previous: String?) {
        if (previous == null || previous == "null" || previous.isEmpty()) {
            adb.shell(serial, "settings", "delete", "global", Cohorts.SETTING_KEY)
        } else {
            adb.shell(serial, "settings", "put", "global", Cohorts.SETTING_KEY, previous)
        }
    }

    /** Classify the pass's launches from the captured tap output; a violation is logged loudly, never fatal. */
    private fun checkCohorts(p: Int, capture: Path) {
        val text = if (Files.exists(capture)) Files.readString(capture) else ""
        val report = Cohorts.report(text, method)
        Files.writeString(
            campDir.resolve("pass$p-cohorts.json"),
            StartupJson.encodeToString(JsonObject.serializer(), Cohorts.toJson(report, method)),
        )
        log("pass $p ${report.summary}")
        if (report.violations.isNotEmpty()) {
            log("pass $p WARN: ${report.violations.size} launch(es) took the wrong user-session path for $method - drop or split them")
        }
        if (report.launches.isEmpty()) {
            log("pass $p WARN: no sdk-init spans in the tap capture - is the app under test the ExampleApp with TelemetryVerificationTap?")
        }
    }

    /** One pass: cool gate, benchmark (with the single install-timeout retry), copy traces aside, verify cohorts. */
    private fun runPass(p: Int, baseline: Double?): Boolean {
        if (p > 1) {
            coolDown(baseline)
        }
        log("pass $p/$passes starting, battery ${Thermal.battery(adb, serial)}, ${Thermal.silicon(adb, serial)}")
        val t0 = System.nanoTime()
        val capture = campDir.resolve("pass$p-embverify.log")
        val tap = if (verifyCohort) logcat(listOf("adb", "-s", serial) + Cohorts.LOGCAT_ARGS, capture) else null
        val out = try {
            runBenchmarkWithRetry(p, baseline)
        } finally {
            tap?.close()
        }
        if (out.exitCode != 0) {
            log("pass $p FAILED (exit ${out.exitCode}); aborting")
            return false
        }
        val src = resolveTraceDir(connected, dirMatch, ::log) ?: return false
        val dest = campDir.resolve("pass$p")
        copyTree(src, dest)
        val n = Files.list(dest).use { s -> s.filter { it.fileName.toString().endsWith(".perfetto-trace") }.count() }
        val mins = PyFormat.fixed((System.nanoTime() - t0) / NANOS_PER_MINUTE, 1)
        log("pass $p done in $mins min, $n traces, battery ${Thermal.battery(adb, serial)}, ${Thermal.silicon(adb, serial)}")
        if (verifyCohort) {
            checkCohorts(p, capture)
        }
        if (gapAfterPass == p) {
            log("idle gap: 300 s")
            sleep(IDLE_GAP_MS)
        }
        return true
    }

    private fun runBenchmarkWithRetry(p: Int, baseline: Double?): Processes.Output {
        var out = runBenchmark(campDir.resolve("pass$p-gradle.log"))
        if (out.exitCode != 0 && INSTALL_TIMEOUT_MARKER in out.stdout) {
            // Entry-tier devices intermittently blow ddmlib's install-commit timeout - neither thermal
            // nor space, a slow-eMMC timeout. Retried ONCE, and only for this specific signature.
            log("pass $p hit the install-timeout signature; retrying once")
            coolDown(baseline)
            out = runBenchmark(campDir.resolve("pass$p-gradle-retry.log"))
        }
        return out
    }

    fun benchmarkCommand(): List<String> = listOf(
        app.resolve("gradlew").toString(),
        "-p",
        app.toString(),
        ":app:benchmark:connectedBenchmarkAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.class=io.embrace.android.benchmark.StartupBenchmarks#$method",
        "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=false",
        "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=LOW-BATTERY",
    )

    private fun runBenchmark(logFile: Path): Processes.Output {
        val out = gradle(benchmarkCommand(), repo, mapOf("ANDROID_SERIAL" to serial))
        Files.writeString(logFile, out.stdout + "\n--- stderr ---\n" + out.stderr)
        return out
    }

    /** The device's RESTING silicon temperature: poll until it stops falling, so a warm start does not bake heat into the gate. */
    fun settleBaseline(): Double? {
        var prev = Thermal.maxSilicon(adb, serial) ?: return null
        var waited = 0L
        while (waited < MAX_COOL_WAIT_S) {
            sleep(COOL_POLL_S * MILLIS)
            waited += COOL_POLL_S
            val now = Thermal.maxSilicon(adb, serial) ?: return prev
            if (now >= prev - SETTLE_TOLERANCE_C) {
                return minOf(prev, now)
            }
            prev = now
        }
        return prev
    }

    /** Wait for silicon to return within [COOL_MARGIN_C] of the baseline; best-effort, always logged. */
    fun coolDown(baseline: Double?) {
        if (baseline == null) {
            log("cool gate: no silicon baseline available - proceeding without it")
            return
        }
        val target = baseline + COOL_MARGIN_C
        var waited = 0L
        while (waited < MAX_COOL_WAIT_S) {
            val now = Thermal.maxSilicon(adb, serial)
            if (now == null) {
                log("cool gate: silicon unreadable - proceeding")
                return
            }
            if (now <= target) {
                if (waited > 0) {
                    log("cool gate: ${PyFormat.fixed(now, 1)}C <= ${PyFormat.fixed(target, 1)}C after ${waited}s")
                }
                return
            }
            sleep(COOL_POLL_S * MILLIS)
            waited += COOL_POLL_S
        }
        val now = Thermal.maxSilicon(adb, serial)?.let { PyFormat.fixed(it, 1) } ?: "None"
        log("cool gate: TIMED OUT after ${waited}s at ${now}C (target ${PyFormat.fixed(target, 1)}C) - proceeding, but this pass ran warm")
    }

    private fun writeRunMetadata() {
        val catalog = app.resolve("gradle/libs.versions.toml")
        val pin = if (Files.exists(catalog)) {
            Files.readAllLines(catalog).firstOrNull { it.trim().startsWith("embrace =") }?.trim()
        } else {
            null
        }
        val sdkVersion = pin?.substringAfter('"', "")?.substringBefore('"')?.ifEmpty { null }
        val head = runCatching {
            Processes.run(listOf("git", "-C", repo.toString(), "rev-parse", "HEAD")).stdout.trim()
        }.getOrNull()
        // The device PROFILE and the compile state travel with the run so `ingest` can check drift and
        // fill the recipe without a cell-state.json; the Python left both to the operator.
        val profile = runCatching { DeviceProbe(adb).profile(serial) }.getOrNull()
        val profileJson: JsonElement = if (profile == null) {
            JsonObject(emptyMap())
        } else {
            StartupJson.encodeToJsonElement(DeviceProfile.serializer(), profile)
        }
        val levels = LinkedHashMap<String, JsonElement>()
        compileStateOf(method)?.let { levels["compile"] = JsonPrimitive(it) }
        val runShape = JsonObject(mapOf("passes" to JsonPrimitive(passes), "iterations" to jsonOrNull(iterations)))
        val meta = JsonObject(
            mapOf(
                "serial" to JsonPrimitive(serial),
                "method" to JsonPrimitive(method),
                "build_type" to JsonPrimitive("benchmark"),
                "run_shape" to runShape,
                "device_profile" to profileJson,
                "cell" to JsonObject(mapOf("levels" to JsonObject(levels))),
                "catalog_pin" to jsonOrNull(pin),
                "sdk_version" to jsonOrNull(sdkVersion),
                "repo_head" to jsonOrNull(head),
                "started" to JsonPrimitive(LocalDateTime.now().format(ISO)),
            ),
        )
        Files.writeString(campDir.resolve("run-metadata.json"), StartupJson.encodeToString(JsonObject.serializer(), meta))
    }

    private fun jsonOrNull(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Int -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private fun log(msg: String) {
        val line = "${LocalDateTime.now().format(CLOCK)} $msg"
        println(line)
        Files.writeString(campDir.resolve("campaign.log"), line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    companion object {
        const val COOL_MARGIN_C: Double = 8.0
        const val COOL_POLL_S: Long = 30
        const val MAX_COOL_WAIT_S: Long = 900
        const val INSTALL_TIMEOUT_MARKER: String = "Failed to install split APK"
        const val CONNECTED_SUBPATH: String =
            "app/benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected"
        private const val SETTLE_TOLERANCE_C = 1.0
        private const val IDLE_GAP_MS = 300_000L
        private const val MILLIS = 1000L
        private const val NANOS_PER_MINUTE = 60e9

        /**
         * The compile state a StartupBenchmarks method establishes, in the reference recipe's vocabulary.
         * `coldStartup` runs CompilationMode.DEFAULT - the fresh-install state, which is `verify` on most
         * builds and never full AOT - so it is `default`, not `full`; `full` is `coldStartupFullAot`. The
         * two user-session arms compile with the baseline profile like the arm they derive from.
         */
        internal fun compileStateOf(method: String): String? = when (method) {
            "coldStartupBaselineProfile",
            "coldStartupBaselineProfileNewUserSession",
            "coldStartupBaselineProfileExpiredUserSession",
            -> "profile"
            "coldStartupNoAot" -> "none"
            "coldStartupFullAot" -> "full"
            "coldStartup" -> "default"
            else -> null
        }

        private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        /**
         * Find the run's output directory under `connected/`, tolerantly. Macrobenchmark names it from
         * the device's model string plus API level ('SM-A145M - 15'); hints are written the other way
         * ('SM_A145'). A plain substring test failed AFTER a pass was measured and wiped a four-device
         * campaign. So: compare with separators and case normalised away, fall back to the
         * sole directory present (gradle wipes it per run), and log both non-exact outcomes loudly.
         */
        fun resolveTraceDir(connected: Path, match: String, log: (String) -> Unit): Path? {
            if (!Files.isDirectory(connected)) {
                log("no output directory at $connected; the benchmark produced nothing - aborting")
                return null
            }
            val candidates = Files.list(connected).use { s -> s.filter { Files.isDirectory(it) }.toList() }
                .map { it.fileName.toString() }.sorted()
            if (candidates.isEmpty()) {
                log("$connected exists but is empty; the run produced no per-device output - aborting")
                return null
            }
            val wanted = norm(match)
            val hits = candidates.filter { wanted in norm(it) }
            return when {
                hits.size == 1 -> connected.resolve(hits[0])
                hits.size > 1 -> {
                    log(
                        "hint '$match' matches ${hits.size} directories ${pyList(hits)}; refusing to guess which " +
                            "device's traces these are - narrow the hint - aborting",
                    )
                    null
                }
                candidates.size == 1 -> {
                    log(
                        "hint '$match' matched nothing, but exactly one output directory exists " +
                            "('${candidates[0]}') and gradle wipes this directory per run, so it is this run's - " +
                            "using it. Fix the hint to silence this: the real name is what the device reports.",
                    )
                    connected.resolve(candidates[0])
                }
                else -> {
                    log(
                        "hint '$match' matched none of ${pyList(candidates)} under $connected (comparison ignores case " +
                            "and separators, so this is a genuinely different name, not a '_' vs '-' problem) - aborting",
                    )
                    null
                }
            }
        }

        fun copyTree(src: Path, dest: Path) {
            Files.walk(src).use { stream ->
                stream.forEach { p ->
                    val target = dest.resolve(src.relativize(p).toString())
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(target)
                    } else {
                        Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }

        private fun norm(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

        private fun pyList(items: List<String>) = items.joinToString(", ", "[", "]") { "'$it'" }
    }
}
