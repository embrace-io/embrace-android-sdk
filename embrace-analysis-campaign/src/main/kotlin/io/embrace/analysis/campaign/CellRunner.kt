package io.embrace.analysis.campaign

import io.embrace.analysis.common.json.PyJson
import io.embrace.analysis.common.json.StartupJson
import io.embrace.analysis.common.proc.Processes
import io.embrace.analysis.device.Adb
import io.embrace.analysis.perfetto.Prebuilt
import io.embrace.analysis.perfetto.TraceProcessor
import io.embrace.analysis.records.store.RecordsRoot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Runs ONE matrix cell - set the factor state, machine-check every [Invariant], run the passes through
 * [FleetCampaign] (in-process, no CLI seam to drift), verify the traces carry the instrument
 * ([InstrumentPresent]), and record provenance in `cell-state.json` - the file `ingest` reads first.
 *
 * A failed invariant is a STOP, not a warning: every one of them has already caused a wasted or wrong
 * campaign. The invariants, in the order they run: [HostQuiet] (no second driver alive), [DeviceQuiet]
 * (nothing of ours left on the phone), [SdkMatch] (the resolved SDK coordinate read back from Gradle,
 * never the catalog text), [Temperature] (thermalservice only; battery is not a control input) and, when
 * the app is installed, [CompileState] (from `dumpsys package dexopt`).
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

    /** Runs the cell; throws [Abort] with the refused invariant's message. Returns the cell directory. */
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
            val present = InstrumentPresent(traceProcessor).check(target.cellDir, instrument)
            log(checkLine(present), target.logFile)
            provenance["instrument_check"] = JsonPrimitive(present.detail)
            provenance["finished"] = JsonPrimitive(now())
            writeState(target.cellDir, provenance)
            if (!present.ok) {
                abort("ABORT: expected window instrument missing - quarantine this cell")
            }
            log("cell complete: ${target.cellDir}", target.logFile)
            return target.cellDir
        } finally {
            Files.deleteIfExists(lockFile)
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

    // --------------------------------------------------------------------------- the run

    private fun resolve(cellsFile: Path, cellId: String, outDir: Path?): Target {
        val doc = StartupJson.parseToJsonElement(Files.readString(cellsFile)).jsonObject
        val plan = doc.getValue("plan").jsonObject
        val cell = doc.getValue("cells").jsonArray.map { it.jsonObject }.firstOrNull { PyJson.strOrNull(it, "id") == cellId }
            ?: abort("no such cell: $cellId")
        val runId = PyJson.str(plan, "run_id", "None")
        val cellDir = (outDir ?: RecordsRoot.claudeOutput(repo).resolve("vfm-$runId")).resolve(cellDirName(cellId))
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

    /** The invariants for one target, in the order they are checked and logged. */
    private fun invariantsOf(t: Target): List<Invariant> {
        val toLog: (String) -> Unit = { log(it, t.logFile) }
        val invariants = ArrayList<Invariant>()
        invariants.add(HostQuiet(processList))
        invariants.add(DeviceQuiet(adb, t.serial, toLog))
        invariants.add(SdkMatch(repo, gradle, PyJson.str(t.cell, "version", "None"), toLog))
        invariants.add(Temperature(adb, t.serial, t.coolGateC, PyJson.strOrNull(t.levels, "thermal")))
        if (adb.shell(t.serial, "pm", "list", "packages", PKG).isNotEmpty()) {
            invariants.add(CompileState(adb, t.serial, PKG, PyJson.strOrNull(t.levels, "compile")))
        }
        return invariants
    }

    private fun runChecks(t: Target): List<Check> {
        val checks = invariantsOf(t).map { it.check() }
        checks.forEach { log(checkLine(it), t.logFile) }
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

    private fun checkLine(check: Check): String {
        val mark = if (check.ok) {
            "OK "
        } else {
            "FAIL"
        }
        return "  [$mark] ${check.name}: ${check.detail}"
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
        private const val SHA_BUFFER_SHIFT = 20
        private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        private val PRETTY = kotlinx.serialization.json.Json(StartupJson) { prettyPrint = true }

        /** `a14|local|reference` → `a14__local__reference`; `,`→`_`, `=`→`-`. */
        fun cellDirName(cellId: String): String = cellId.replace("|", "__").replace(",", "_").replace("=", "-")
    }
}
