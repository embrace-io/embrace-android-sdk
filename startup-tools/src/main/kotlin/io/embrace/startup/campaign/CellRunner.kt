package io.embrace.startup.campaign

import io.embrace.startup.core.json.PyJson
import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.proc.Processes
import io.embrace.startup.core.text.PyFormat
import io.embrace.startup.device.Adb
import io.embrace.startup.perfetto.Prebuilt
import io.embrace.startup.perfetto.TraceHealth
import io.embrace.startup.perfetto.TraceProcessor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.streams.toList

/**
 * `cell_runner.py`: run ONE matrix cell - set the factor state, machine-check every invariant, run
 * the passes through [FleetCampaign] (in-process, no CLI seam to drift), and record provenance in
 * `cell-state.json` - the file `ingest` reads first.
 *
 * A failed invariant is a STOP, not a warning: every one of them has already caused a wasted or wrong
 * campaign. Resolved SDK coordinate read back from Gradle (never the catalog text); compile state from
 * `dumpsys package dexopt`; temperature from thermalservice only (battery is not a control input);
 * host quiet (no second driver alive).
 *
 * Two departures from the Python, both in the port log: the instrument canary for the post-run check
 * comes from the plan's `instrument` (default `emb-sdk-start`) where the Python hard-coded
 * `app-embrace-start`, a slice the harness never emitted; and the temperature parser matches
 * `mValue=` anywhere in a `Temperature{...}` line, where the Python required a comma-split part to
 * START with it - the first part starts with `Temperature{`, so the Python never read a sensor and the
 * temperature invariant could never pass.
 */
class CellRunner(
    private val repo: Path,
    private val adb: Adb = Adb(),
    private val gradle: (List<String>, Path) -> Processes.Output = { cmd, cwd -> Processes.run(cmd, cwd) },
    private val processList: () -> String = { Processes.run(listOf("ps", "-axo", "pid,command")).stdout },
    private val campaign: (
        serial: String,
        cellDir: Path,
        passes: Int,
        method: String,
        iterations: Int,
    ) -> Int = { s, d, p, m, i ->
        FleetCampaign(serial = s, dirMatch = "", campDir = d, passes = p, method = m, iterations = i, repo = repo).run()
    },
    private val lockFile: Path = Path.of(System.getProperty("java.io.tmpdir"), "startup-matrix-cell.pid"),
    private val traceProcessor: (() -> TraceProcessor)? = { TraceProcessor(Prebuilt.resolve()) },
) {
    class Abort(message: String) : RuntimeException(message)

    data class Check(val name: String, val ok: Boolean, val detail: String)

    /** Everything resolved from the cells file before any device is touched. */
    private class Target(
        val plan: JsonObject,
        val cell: JsonObject,
        val cellId: String,
        val runId: String,
        val cellDir: Path,
        val serial: String,
        val deviceCfg: JsonObject,
        val coolGateC: Double,
    ) {
        val logFile: Path get() = cellDir.resolve("cell.log")
        val levels: JsonObject get() = PyJson.obj(cell, "levels") ?: JsonObject(emptyMap())
        val buildType: String get() = PyJson.str(plan, "build_type", "benchmark")
    }

    private val example: Path = repo.resolve("examples/ExampleApp")
    private val catalog: Path = example.resolve("gradle/libs.versions.toml")

    /** Runs the cell; throws [Abort] with the Python's message on any refused invariant. Returns the cell directory. */
    fun run(cellsFile: Path, cellId: String, outDir: Path?, checkOnly: Boolean): Path {
        val target = resolve(cellsFile, cellId, outDir)
        acquireLock()
        try {
            log("cell $cellId levels=${PyJson.dumps(target.levels, sortKeys = false)}", target.logFile)
            val checks = runChecks(target)
            val provenance = provenanceOf(target, checks)
            writeState(target.cellDir, provenance)
            if (checkOnly) {
                log("check-only: invariants passed; not running passes", target.logFile)
                return target.cellDir
            }
            runPasses(target)
            val instrument = PyJson.str(target.plan, "instrument", DEFAULT_INSTRUMENT)
            val (ok, detail) = checkInstrument(target.cellDir, instrument)
            log("  [${if (ok) "OK " else "FAIL"}] instrument: $detail", target.logFile)
            provenance["instrument_check"] = JsonPrimitive(detail)
            provenance["finished"] = JsonPrimitive(now())
            writeState(target.cellDir, provenance)
            if (!ok) {
                abort("ABORT: expected window instrument missing - quarantine this cell")
            }
            log("cell complete: ${target.cellDir}", target.logFile)
            return target.cellDir
        } finally {
            Files.deleteIfExists(lockFile)
        }
    }

    private fun resolve(cellsFile: Path, cellId: String, outDir: Path?): Target {
        val doc = StartupJson.parseToJsonElement(Files.readString(cellsFile)).jsonObject
        val plan = doc.getValue("plan").jsonObject
        val cell = doc.getValue("cells").jsonArray.map { it.jsonObject }.firstOrNull { PyJson.strOrNull(it, "id") == cellId }
            ?: abort("no such cell: $cellId")
        val runId = PyJson.str(plan, "run_id", "None")
        val cellDir = (outDir ?: repo.resolve("claude-output").resolve("vfm-$runId")).resolve(cellDirName(cellId))
        Files.createDirectories(cellDir)
        // Combo cells deliberately run on another device; defaulting to the primary serial would
        // silently measure the wrong one, so the cell's own device must be configured.
        val devices = PyJson.obj(plan, "devices") ?: JsonObject(emptyMap())
        val deviceKey = PyJson.str(cell, "device", "None")
        val devCfg = PyJson.obj(devices, deviceKey)
        val serial = PyJson.strOrNull(devCfg, "serial")
            ?: abort(
                "ABORT: no serial configured for device '$deviceKey' (known: ${PyJson.reprList(devices.keys.sorted())}) - " +
                    "fix the plan's devices map",
            )
        if (serial !in adb.run(null, "devices").stdout) {
            abort("ABORT: device $deviceKey ($serial) is not attached")
        }
        return Target(
            plan,
            cell,
            cellId,
            runId,
            cellDir,
            serial,
            checkNotNull(devCfg),
            PyJson.double(devCfg, "cool_gate_c") ?: DEFAULT_COOL_GATE_C,
        )
    }

    private fun runChecks(t: Target): List<Check> {
        val checks = ArrayList<Check>()
        checks.add(checkHostQuiet())
        checks.add(checkSdkMatches(PyJson.str(t.cell, "version", "None"), t.logFile))
        checks.add(checkTemperature(t.serial, t.coolGateC, PyJson.strOrNull(t.levels, "thermal")))
        if (adb.shell(t.serial, "pm", "list", "packages", PKG).isNotEmpty()) {
            checks.add(checkCompileState(t.serial, PyJson.strOrNull(t.levels, "compile")))
        }
        checks.forEach { log("  [${if (it.ok) "OK " else "FAIL"}] ${it.name}: ${it.detail}", t.logFile) }
        val failed = checks.filter { !it.ok }.map { it.name }
        if (failed.isNotEmpty()) {
            abort("ABORT: invariants failed: ${PyJson.reprList(failed)} - fix, do not proceed")
        }
        return checks
    }

    private fun provenanceOf(t: Target, checks: List<Check>): LinkedHashMap<String, JsonElement> {
        val provenance = LinkedHashMap<String, JsonElement>()
        provenance["cell"] = t.cell
        provenance["plan_run_id"] = JsonPrimitive(t.runId)
        provenance["serial"] = JsonPrimitive(t.serial)
        // The device PROFILE travels with every result: comparability across runs, machines and
        // people depends on it, and the longitudinal skill consumes it.
        provenance["device_profile"] = JsonObject(t.deviceCfg.filterKeys { it != "serial" })
        provenance["build_type"] = JsonPrimitive(t.buildType)
        provenance["apk_sha256"] = apkSha256(t.buildType)?.let { JsonPrimitive(it) } ?: JsonNull
        provenance["repo_head"] = JsonPrimitive(git("rev-parse", "HEAD"))
        provenance["repo_dirty"] = JsonPrimitive(git("status", "--short").isNotEmpty())
        provenance["catalog_pin"] = catalogPin()?.let { JsonPrimitive(it) } ?: JsonNull
        provenance["checks"] = JsonObject(checks.associate { it.name to JsonPrimitive(it.detail) })
        provenance["started"] = JsonPrimitive(now())
        return provenance
    }

    private fun runPasses(t: Target) {
        val (saved, hogs) = applyFactorState(t.serial, t.levels, t.logFile)
        try {
            val method = if (PyJson.strOrNull(t.levels, "compile") == "profile") "coldStartupBaselineProfile" else "coldStartup"
            val passes = PyJson.double(t.plan, "passes")?.toInt() ?: 0
            val iterations = PyJson.double(t.plan, "iterations")?.toInt() ?: 0
            log(
                "delegating passes: fleet-campaign --serial ${t.serial} --out ${t.cellDir} --passes $passes " +
                    "--method $method --iterations $iterations",
                t.logFile,
            )
            val rc = campaign(t.serial, t.cellDir, passes, method, iterations)
            log("campaign rc=$rc", t.logFile)
        } finally {
            restoreState(t.serial, saved, hogs, t.logFile)
        }
    }

    // --------------------------------------------------------------------------- invariants

    /**
     * No competing driver in flight: another campaign or cell runner, Python or Kotlin. This process
     * and its ancestors are excluded - the shell that launched `cell-runner` carries the marker in its
     * own command line, which the first real check-only run tripped over.
     */
    fun checkHostQuiet(): Check {
        val mine = HashSet<String>()
        var handle: java.util.Optional<ProcessHandle> = java.util.Optional.of(ProcessHandle.current())
        while (handle.isPresent) {
            mine.add(handle.get().pid().toString())
            handle = handle.get().parent()
        }
        val others = processList().lines().filter { line ->
            val pid = line.trim().substringBefore(" ")
            DRIVER_MARKERS.any { it in line } && pid !in mine
        }
        return if (others.isEmpty()) {
            Check("host quiet", true, "host quiet")
        } else {
            Check("host quiet", false, "other drivers alive: ${PyJson.reprList(others.take(2))}")
        }
    }

    /** thermalservice only; `dumpsys battery` is not a control input (some devices freeze it). */
    fun checkTemperature(serial: String, gateC: Double, band: String?): Check {
        val temps = parseTemperatures(adb.shell(serial, "dumpsys", "thermalservice"))
        val plausible = temps.filter { it > PLAUSIBLE_MIN_C && it < PLAUSIBLE_MAX_C }
        if (plausible.isEmpty()) {
            return Check("temperature", false, "no plausible thermalservice sensor values (do not fall back to battery)")
        }
        val hottest = plausible.max()
        val shown = PyFormat.fixed(hottest, 1)
        return when {
            band == "hot" -> Check("temperature", true, "hot cell: hottest sensor $shown C (band enforced by the heater)")
            hottest > gateC -> Check("temperature", false, "hottest sensor $shown C > gate ${pyNum(gateC)} C")
            else -> Check("temperature", true, "hottest sensor $shown C <= gate ${pyNum(gateC)} C")
        }
    }

    /** The SDK coordinate the app actually resolves - never the catalog text. */
    fun resolvedSdkVersion(): String? {
        val gradlew = example.resolve("gradlew").toString()
        val out = gradle(
            listOf(gradlew, "-p", example.toString(), ":app:dependencies", "--configuration", "benchmarkRuntimeClasspath", "-q"),
            example,
        )
        return resolvedSdkLine(out.stdout)
    }

    fun checkSdkMatches(expected: String, logFile: Path): Check {
        val resolved = resolvedSdkVersion()
            ?: return Check("sdk matches", false, "could not read a resolved embrace-android-sdk coordinate")
        log("resolved SDK: $resolved", logFile)
        if (expected == "local") {
            val local = Files.readAllLines(repo.resolve("gradle.properties")).firstOrNull { it.startsWith("version=") }
                ?.substringAfter("=")?.trim()
            return Check("sdk matches", local != null && local in resolved, "expected local ${local ?: "None"}, resolved $resolved")
        }
        return Check("sdk matches", expected in resolved, "expected $expected, resolved $resolved")
    }

    fun checkCompileState(serial: String, want: String?): Check {
        val status = dexoptStatus(adb.shell(serial, "dumpsys", "package", "dexopt"), PKG)
            ?: return Check("compile state", false, "no dexopt status for the package (is it installed?)")
        val ok = when (want) {
            "profile" -> "speed-profile" in status
            "none" -> "verify" in status || "run-from-apk" in status
            "full" -> "speed" in status && "speed-profile" !in status
            else -> true
        }
        return Check("compile state", ok, status)
    }

    /**
     * Two failures look identical in the output and must be told apart: a wrong SDK/patch (the window
     * instrument does not exist) and a saturated capture (it existed but got evicted). Canary AND loss counters.
     */
    fun checkInstrument(traceDir: Path, instrument: String): Pair<Boolean, String> {
        val traces = Files.walk(traceDir).use { s ->
            s.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".perfetto-trace") }.toList()
        }.sorted()
        if (traces.isEmpty()) return false to "no traces produced"
        val tp = runCatching { traceProcessor?.invoke() }.getOrNull()
            ?: return true to "${traces.size} traces (trace_processor unavailable; UNVERIFIED)"
        // Sample the ends of the cell: saturation usually worsens as a pass accumulates load.
        val sample = if (traces.size > SAMPLE_EDGE * 2) {
            traces.take(SAMPLE_EDGE) + traces.takeLast(SAMPLE_EDGE)
        } else {
            traces
        }
        val verdicts = sample.map { TraceHealth.check(tp, it, instrument) }
        val report = TraceHealth.summarize(verdicts)
        val saturated = "$report | capture saturated and evicted the window - fix the trace config " +
            "(buffer size / DISCARD / narrower events) before re-running"
        val noCanary = "$report | no data loss but no '$instrument' anywhere - wrong SDK, wrong build, " +
            "or a missing atrace category"
        return when {
            verdicts.any { it.verdict == TraceHealth.Verdict.UNUSABLE } -> false to saturated
            verdicts.all { it.verdict == TraceHealth.Verdict.MISSING_CANARY } -> false to noCanary
            else -> true to report
        }
    }

    // --------------------------------------------------------------------------- factor state

    /** Device-side state only; compile state comes from the benchmark and install state from the install sequence. */
    fun applyFactorState(serial: String, levels: JsonObject, logFile: Path): Pair<Map<String, String>, List<Process>> {
        val saved = mapOf(
            "stay_on" to adb.shell(serial, "settings", "get", "global", "stay_on_while_plugged_in"),
            "airplane" to adb.shell(serial, "settings", "get", "global", "airplane_mode_on"),
        )
        adb.shell(serial, "settings", "put", "global", "stay_on_while_plugged_in", "7")
        adb.shell(serial, "svc", "wifi", "disable")
        adb.shell(serial, "input", "keyevent", "224")
        adb.shell(serial, "wm", "dismiss-keyguard")
        val contention = PyJson.str(levels, "contention", "quiet")
        if (contention == "quiet") return saved to emptyList()
        // Scale the load to the device: "queueing" exceeds the core count to fill the run queue,
        // "bandwidth" stays below it and pressures the memory system without queueing.
        val present = adb.shell(serial, "cat", "/sys/devices/system/cpu/present")
        val cores = present.substringAfter("-", "").trim().toIntOrNull()?.plus(1) ?: DEFAULT_CORES
        val n = if (contention == "queueing") cores * 2 else maxOf(2, cores / 2)
        val hogs = (1..n).map {
            ProcessBuilder("adb", "-s", serial, "shell", "dd if=/dev/zero of=/dev/null")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }
        log("contention: $n hogs started ($contention)", logFile)
        return saved to hogs
    }

    fun restoreState(serial: String, saved: Map<String, String>, hogs: List<Process>, logFile: Path) {
        hogs.forEach { it.destroyForcibly() }
        adb.shell(serial, "pkill", "-9", "dd")
        adb.shell(serial, "svc", "wifi", "enable")
        saved["stay_on"]?.takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }?.let {
            adb.shell(serial, "settings", "put", "global", "stay_on_while_plugged_in", it)
        }
        log("device state restored", logFile)
    }

    // --------------------------------------------------------------------------- helpers

    private fun acquireLock() {
        if (Files.exists(lockFile)) {
            val pid = Files.readString(lockFile).trim().toLongOrNull()
            val alive = pid != null && ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
            if (alive) {
                abort("ABORT: another cell runner is alive (pid $pid); $lockFile")
            }
            println("stale lock $lockFile ignored")
        }
        Files.writeString(lockFile, ProcessHandle.current().pid().toString())
    }

    private fun apkSha256(buildType: String): String? {
        val apk = example.resolve("app/build/outputs/apk/$buildType/app-$buildType.apk")
        if (!Files.exists(apk)) return null
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(apk).use { input ->
            val buf = ByteArray(1 shl SHA_BUFFER_SHIFT)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun catalogPin(): String? =
        if (Files.exists(catalog)) {
            Files.readAllLines(catalog).firstOrNull {
                it.trim().startsWith("embrace =")
            }?.trim()
        } else {
            null
        }

    private fun git(vararg args: String): String =
        runCatching { Processes.run(listOf("git", "-C", repo.toString()) + args).stdout.trim() }.getOrDefault("")

    private fun writeState(cellDir: Path, provenance: Map<String, JsonElement>) {
        Files.writeString(cellDir.resolve("cell-state.json"), PRETTY.encodeToString(JsonObject.serializer(), JsonObject(provenance)))
    }

    private fun log(msg: String, logFile: Path?) {
        val line = "${LocalDateTime.now().format(CLOCK)} [cell] $msg"
        println(line)
        logFile?.let { Files.writeString(it, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND) }
    }

    private fun now(): String = LocalDateTime.now().format(ISO)

    private fun abort(message: String): Nothing = throw Abort(message)

    companion object {
        const val PKG: String = "io.embrace.android.exampleapp"
        const val DEFAULT_INSTRUMENT: String = "emb-sdk-start"
        const val DEFAULT_COOL_GATE_C: Double = 32.0
        private const val DEFAULT_CORES = 8
        private const val PLAUSIBLE_MIN_C = 10.0
        private const val PLAUSIBLE_MAX_C = 100.0
        private const val SAMPLE_EDGE = 5
        private const val SHA_BUFFER_SHIFT = 20
        private val DRIVER_MARKERS =
            listOf("fleet_campaign", "fleet-campaign", "device_driver", "p6b", "cell_runner", "cell-runner")
        private val M_VALUE = Regex("mValue=([\\-\\d.]+)")
        private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        private val PRETTY = kotlinx.serialization.json.Json(StartupJson) { prettyPrint = true }

        /** `a14|local|reference` → `a14__local__reference`; `,`→`_`, `=`→`-`. */
        fun cellDirName(cellId: String): String = cellId.replace("|", "__").replace(",", "_").replace("=", "-")

        /**
         * Every `mValue=` on a `Temperature{...}` line of the "Current temperatures from HAL" block. The
         * Python scanned the whole dump, which on some devices also contains a "Cached temperatures" block
         * holding peak values (72 °C at idle) - a gate reading those would never open after a hot pass.
         */
        fun parseTemperatures(dumpsys: String): List<Double> =
            io.embrace.startup.device.Topology.halTemperatureLines(dumpsys).map { it.trim() }
                .filter { it.startsWith("Temperature{") }
                .flatMap { line -> M_VALUE.findAll(line).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList() }

        /** The `io.embrace:embrace-android-sdk` line of a Gradle dependency tree, tree glyphs stripped. */
        fun resolvedSdkLine(stdout: String): String? =
            stdout.lines().firstOrNull {
                "io.embrace:embrace-android-sdk" in it
            }?.trim()?.trimStart('+', '\\', '-', '|', ' ')?.trim()

        /** The `status=` line of the package's block in `dumpsys package dexopt`, or null. */
        fun dexoptStatus(dumpsys: String, pkg: String): String? {
            val lines = dumpsys.lines().map { it.trim() }
            val start = lines.indexOfFirst { pkg in it && it.startsWith("[") }
            if (start < 0) return null
            val block = lines.drop(start + 1).takeWhile { !(it.startsWith("[") && pkg !in it) }
            return block.firstOrNull { "status=" in it }
        }

        /** Python `str()` of the gate: `32.0` stays `32.0`. */
        private fun pyNum(x: Double): String = if (x == Math.rint(x)) PyFormat.fixed(x, 1) else x.toString()

        /** For callers that need to distinguish an unreadable engine from a missing one. */
        internal fun engineUnavailable(e: IOException): String = e.message ?: "trace_processor unavailable"
    }
}
